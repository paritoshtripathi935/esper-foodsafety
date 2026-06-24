import { useEffect, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { format, startOfDay, endOfDay, subHours, subDays } from "date-fns";
import clsx from "clsx";
import { useRegistry } from "../hooks/useRegistry";
import type { FoodSafetyEvent, Site } from "../types";

const AI_BASE = (import.meta.env.VITE_AI_API_BASE as string) || "";

// ─── types ─────────────────────────────────────────────────────────────────

type DateRange = "last24h" | "today" | "last7d";

interface UserMessage {
  id: string;
  role: "user";
  text: string;
}
interface AssistantMessage {
  id: string;
  role: "assistant";
  text: string;
  sources?: number;
}
interface ErrorMessage {
  id: string;
  role: "error";
  text: string;
  retryPayload: AskPayload;
}
interface TypingMessage {
  id: string;
  role: "typing";
}
type Message = UserMessage | AssistantMessage | ErrorMessage | TypingMessage;

interface AskPayload {
  question: string;
  site_id: string;
  from: string;
  to: string;
}

// ─── outlet context (same shape as Overview) ─────────────────────────────────

interface ShellContext {
  events: FoodSafetyEvent[];
  sites: Site[];
  selectedSite: string | null;
}

// ─── helpers ──────────────────────────────────────────────────────────────────

function resolveRange(range: DateRange): { from: string; to: string } {
  const now = new Date();
  switch (range) {
    case "last24h":
      return { from: subHours(now, 24).toISOString(), to: now.toISOString() };
    case "today":
      return {
        from: startOfDay(now).toISOString(),
        to: endOfDay(now).toISOString(),
      };
    case "last7d":
      return {
        from: startOfDay(subDays(now, 7)).toISOString(),
        to: endOfDay(now).toISOString(),
      };
  }
}

function uid() {
  return Math.random().toString(36).slice(2);
}

const SUGGESTIONS = [
  "How many alerts today?",
  "What corrective actions were logged for walk-in-cooler-1?",
  "Which stations had temperature breaches in the last 24 hours?",
];

const RANGE_OPTIONS: Array<{ value: DateRange; label: string }> = [
  { value: "last24h", label: "Last 24h" },
  { value: "today", label: "Today" },
  { value: "last7d", label: "Last 7 days" },
];

// ─── sub-components ───────────────────────────────────────────────────────────

function TypingIndicator() {
  return (
    <div className="flex items-center gap-1 px-4 py-3 rounded-2xl rounded-tl-sm bg-surface-container-high w-fit">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="w-1.5 h-1.5 rounded-full bg-on-surface-variant animate-bounce"
          style={{ animationDelay: `${i * 120}ms` }}
        />
      ))}
    </div>
  );
}

interface BubbleProps {
  msg: Message;
  onRetry: (payload: AskPayload) => void;
}

function Bubble({ msg, onRetry }: BubbleProps) {
  if (msg.role === "typing") {
    return (
      <div className="flex justify-start">
        <TypingIndicator />
      </div>
    );
  }

  if (msg.role === "user") {
    return (
      <div className="flex justify-end">
        <div className="max-w-[80%] px-4 py-2.5 rounded-2xl rounded-tr-sm bg-primary-container text-on-primary-container text-sm leading-relaxed">
          {msg.text}
        </div>
      </div>
    );
  }

  if (msg.role === "error") {
    return (
      <div className="flex justify-start">
        <div className="max-w-[80%] px-4 py-2.5 rounded-2xl rounded-tl-sm bg-error-container text-error text-sm leading-relaxed">
          <p>{msg.text}</p>
          <button
            onClick={() => onRetry(msg.retryPayload)}
            className="mt-1.5 text-xs font-semibold underline underline-offset-2 hover:opacity-80"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  // assistant
  return (
    <div className="flex justify-start">
      <div className="max-w-[80%] flex flex-col gap-1">
        <div className="px-4 py-2.5 rounded-2xl rounded-tl-sm bg-surface-container-high text-on-surface text-sm leading-relaxed whitespace-pre-wrap">
          {msg.text}
        </div>
        {msg.sources !== undefined && (
          <p className="text-[10px] text-on-surface-variant px-1">
            Sources: {msg.sources} event{msg.sources !== 1 ? "s" : ""}
          </p>
        )}
      </div>
    </div>
  );
}

// ─── AskAI ────────────────────────────────────────────────────────────────────

export default function AskAI() {
  const { sites: shellSites, selectedSite: shellSelectedSite } =
    useOutletContext<ShellContext>();
  const { sites: registrySites } = useRegistry();

  // prefer registry (has name), fall back to shell
  const sites = registrySites.length > 0 ? registrySites : shellSites;

  const [siteId, setSiteId] = useState<string>("");
  const [range, setRange] = useState<DateRange>("last24h");
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // default site to shell selected when it becomes known
  useEffect(() => {
    if (!siteId && shellSelectedSite) setSiteId(shellSelectedSite);
  }, [shellSelectedSite]);

  // scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  async function ask(payload: AskPayload) {
    setLoading(true);

    // remove any existing typing indicator and add a fresh one
    const typingId = uid();
    setMessages((prev) => [
      ...prev.filter((m) => m.role !== "typing"),
      { id: typingId, role: "typing" },
    ]);

    try {
      const res = await fetch(`${AI_BASE}/ai/ask`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        let errText = `Request failed (${res.status})`;
        if (res.status === 502) {
          errText =
            "AI couldn't reach the event log. Check SUPABASE_SERVICE_KEY on EC2.";
        } else {
          try {
            const j = await res.json();
            errText = j.error ?? j.message ?? errText;
          } catch {}
        }
        setMessages((prev) => [
          ...prev.filter((m) => m.role !== "typing"),
          { id: uid(), role: "error", text: errText, retryPayload: payload },
        ]);
        return;
      }

      const data = await res.json();
      const text: string =
        data.answer ?? data.text ?? data.narrative ?? JSON.stringify(data);
      const sources: number | undefined =
        data.sources_used ?? data.events_used ?? undefined;

      setMessages((prev) => [
        ...prev.filter((m) => m.role !== "typing"),
        { id: uid(), role: "assistant", text, sources },
      ]);
    } catch (err) {
      setMessages((prev) => [
        ...prev.filter((m) => m.role !== "typing"),
        {
          id: uid(),
          role: "error",
          text: `Network error: ${(err as Error).message}`,
          retryPayload: payload,
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  function submit(question: string) {
    const q = question.trim();
    if (!q || loading) return;

    const { from, to } = resolveRange(range);
    const payload: AskPayload = {
      question: q,
      site_id: siteId || "site-eastgate",
      from,
      to,
    };

    setMessages((prev) => [...prev, { id: uid(), role: "user", text: q }]);
    setInput("");
    ask(payload);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit(input);
    }
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <div className="flex-1 min-h-0">
        <div className="max-w-3xl mx-auto px-5 py-6 flex flex-col gap-5 h-full">
          {/* ── Header ── */}
          <div>
            <h1 className="text-xl font-bold text-on-surface">Ask the log</h1>
            <p className="text-sm text-on-surface-variant mt-0.5">
              Claude answers questions about your event log.
            </p>
          </div>

          {/* ── Context row ── */}
          <div className="flex items-center gap-2 flex-wrap">
            <select
              value={siteId}
              onChange={(e) => setSiteId(e.target.value)}
              className="px-3 py-1.5 rounded-lg text-sm bg-surface-container-high border border-outline-variant
                text-on-surface focus:outline-none focus:border-primary cursor-pointer"
            >
              <option value="">All sites</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>

            <div className="flex items-center gap-1">
              {RANGE_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  onClick={() => setRange(opt.value)}
                  className={clsx(
                    "px-2.5 py-1 rounded-full text-xs font-semibold transition-colors",
                    range === opt.value
                      ? "bg-primary text-on-primary"
                      : "bg-surface-container-high text-on-surface-variant hover:text-on-surface",
                  )}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* ── Chat panel ── */}
          <div className="flex flex-col flex-1 min-h-0 rounded-2xl border border-outline-variant bg-surface-container overflow-hidden">
            <div className="flex-1 min-h-0 overflow-y-auto p-4 flex flex-col gap-3">
              {messages.length === 0 && (
                <div className="flex-1 flex items-center justify-center text-sm text-on-surface-variant">
                  Ask a question about your HACCP event log.
                </div>
              )}
              {messages.map((msg) => (
                <Bubble key={msg.id} msg={msg} onRetry={ask} />
              ))}
              <div ref={messagesEndRef} />
            </div>

            {/* Divider */}
            <div className="border-t border-outline-variant" />

            {/* ── Input area ── */}
            <div className="p-3 flex flex-col gap-2">
              {/* Suggestion chips */}
              <div className="flex items-center gap-1.5 flex-wrap">
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    disabled={loading}
                    onClick={() => submit(s)}
                    className="px-2.5 py-1 rounded-full text-xs bg-surface-container-high text-on-surface-variant
                      border border-outline-variant hover:border-primary hover:text-on-surface
                      disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                  >
                    {s}
                  </button>
                ))}
              </div>

              {/* Textarea + send */}
              <div className="flex gap-2 items-center">
                <textarea
                  ref={textareaRef}
                  rows={2}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={loading}
                  placeholder="Ask about your event log… (Enter to send, Shift+Enter for newline)"
                  className="flex-1 resize-none px-3 py-2 rounded-xl text-sm bg-surface-container-high
                    border border-outline-variant text-on-surface placeholder:text-on-surface-variant
                    focus:outline-none focus:border-primary disabled:opacity-50
                    scrollbar-thin"
                />
                <button
                  onClick={() => submit(input)}
                  disabled={loading || !input.trim()}
                  className="shrink-0 p-2.5 rounded-xl bg-primary text-on-primary
                    hover:bg-primary/80 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                  aria-label="Send"
                >
                  {loading ? (
                    <div className="w-5 h-5 border-2 border-on-primary border-t-transparent rounded-full animate-spin" />
                  ) : (
                    <svg
                      className="w-5 h-5"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                      strokeWidth={2}
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
                      />
                    </svg>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
