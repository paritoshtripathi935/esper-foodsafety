package com.esper.foodsafety.ai

object LlamaJNI {
    init {
        System.loadLibrary("llama_jni")
    }

    external fun loadModel(modelPath: String, nCtx: Int, nThreads: Int): Long
    external fun infer(handle: Long, prompt: String, maxTokens: Int): String
    external fun freeModel(handle: Long)
}
