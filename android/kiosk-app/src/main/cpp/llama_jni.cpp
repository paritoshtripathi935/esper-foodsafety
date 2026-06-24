#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaHandle {
    llama_model*   model   = nullptr;
    llama_context* ctx     = nullptr;
    const llama_vocab* vocab = nullptr;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_esper_foodsafety_ai_LlamaJNI_loadModel(
        JNIEnv* env, jobject /*obj*/, jstring modelPath, jint nCtx, jint nThreads) {

    llama_backend_init();

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // CPU only

    llama_model* model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load model");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx      = (uint32_t)nCtx;
    cparams.n_threads  = (uint32_t)nThreads;
    cparams.n_threads_batch = (uint32_t)nThreads;

    llama_context* ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    LOGI("Model loaded OK");
    auto* handle = new LlamaHandle{model, ctx, llama_model_get_vocab(model)};
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT jstring JNICALL
Java_com_esper_foodsafety_ai_LlamaJNI_infer(
        JNIEnv* env, jobject /*obj*/, jlong handlePtr, jstring promptStr, jint maxTokens) {

    auto* h = reinterpret_cast<LlamaHandle*>(handlePtr);
    if (!h || !h->model || !h->ctx) {
        return env->NewStringUTF("");
    }

    const char* prompt = env->GetStringUTFChars(promptStr, nullptr);

    // Tokenise
    int n_prompt_tokens = -llama_tokenize(h->vocab, prompt, (int)strlen(prompt),
                                          nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt_tokens);
    if (llama_tokenize(h->vocab, prompt, (int)strlen(prompt),
                       tokens.data(), tokens.size(), true, true) < 0) {
        LOGE("Tokenise failed");
        env->ReleaseStringUTFChars(promptStr, prompt);
        return env->NewStringUTF("");
    }
    env->ReleaseStringUTFChars(promptStr, prompt);

    llama_memory_clear(llama_get_memory(h->ctx), true);

    // Build batch and decode prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(h->ctx, batch) != 0) {
        LOGE("Prompt decode failed");
        return env->NewStringUTF("");
    }

    // Greedy sampling
    auto smpl_params = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(smpl_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.1f));
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string result;
    result.reserve(512);

    llama_token eos = llama_vocab_eos(h->vocab);

    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(sampler, h->ctx, -1);
        if (tok == eos || tok == llama_vocab_eot(h->vocab)) break;

        char buf[256] = {};
        int len = llama_token_to_piece(h->vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) result.append(buf, len);

        // Stop on common stop strings
        if (result.find("<|user|>")   != std::string::npos) break;
        if (result.find("<|system|>") != std::string::npos) break;

        llama_batch next = llama_batch_get_one(&tok, 1);
        if (llama_decode(h->ctx, next) != 0) break;
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_esper_foodsafety_ai_LlamaJNI_freeModel(
        JNIEnv* /*env*/, jobject /*obj*/, jlong handlePtr) {

    auto* h = reinterpret_cast<LlamaHandle*>(handlePtr);
    if (!h) return;
    if (h->ctx)   llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
    llama_backend_free();
    LOGI("Model freed");
}

} // extern "C"
