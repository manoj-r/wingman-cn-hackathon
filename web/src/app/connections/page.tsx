"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api, Introduction, Matches } from "../../lib/api";
import { asError, ErrorNotice } from "../../components/Forms";

const POLL_LIMIT_MS = 45_000;

export default function Connections() {
  const [data, setData] = useState<Matches | null>(null);
  const [error, setError] = useState<ReturnType<typeof asError> | null>(null);
  const [loading, setLoading] = useState(true);
  const [retrying, setRetrying] = useState(false);
  const [selected, setSelected] = useState<Matches["matches"][number] | null>(null);
  const [introduction, setIntroduction] = useState<Introduction | null>(null);
  const [introError, setIntroError] = useState("");
  const [introBusy, setIntroBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const openerRef = useRef<HTMLButtonElement>(null);
  const deadlineRef = useRef(0);

  const load = useCallback(async (automatic = false) => {
    if (!automatic) setLoading(true);
    setError(null);
    try {
      const next = await api<Matches>("/me/matches");
      setData(next);
      setError(null);
      if (next.matchingStatus === "PENDING" || next.matchingStatus === "PROCESSING") {
        if (!deadlineRef.current) deadlineRef.current = Date.now() + POLL_LIMIT_MS;
      } else deadlineRef.current = 0;
    } catch (cause) {
      setError(asError(cause));
      if (!automatic) setData(null);
    } finally {
      if (!automatic) setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!data || !["PENDING", "PROCESSING"].includes(data.matchingStatus) || Date.now() >= deadlineRef.current) return;
    const timer = window.setTimeout(() => { void load(true); }, Math.min((data.pollAfterSeconds || 2) * 1000, Math.max(0, deadlineRef.current - Date.now())));
    return () => window.clearTimeout(timer);
  }, [data, load]);

  const refresh = async () => { deadlineRef.current = 0; await load(); };
  const retry = async () => {
    setRetrying(true);
    setError(null);
    try {
      const queued = await api<{ matchingStatus: "PENDING" | "PROCESSING" }>("/me/matches/retry", { method: "POST", body: "{}" });
      setData(current => current ? { ...current, matchingStatus: queued.matchingStatus, matches: [], retryable: false, pollAfterSeconds: 2 } : current);
      deadlineRef.current = Date.now() + POLL_LIMIT_MS;
      await load(true);
    } catch (cause) { setError(asError(cause)); }
    finally { setRetrying(false); }
  };

  const openIntroduction = async (match: Matches["matches"][number], button: HTMLButtonElement) => {
    setSelected(match);
    setIntroduction(null);
    setIntroError("");
    setCopied(false);
    openerRef.current = button;
    dialogRef.current?.showModal();
    setIntroBusy(true);
    try {
      setIntroduction(await api<Introduction>("/me/introductions", { method: "POST", body: JSON.stringify({ targetAttendeeId: match.attendeeId }) }));
    } catch (cause) { setIntroError(asError(cause).message); }
    finally { setIntroBusy(false); }
  };

  const closeDialog = () => {
    dialogRef.current?.close();
    setSelected(null);
    setIntroduction(null);
    setIntroError("");
    setCopied(false);
    requestAnimationFrame(() => openerRef.current?.focus());
  };

  const copyIntroduction = async () => {
    if (!introduction) return;
    try { await navigator.clipboard.writeText(introduction.text); setCopied(true); }
    catch { setIntroError("Clipboard access is unavailable. Select and copy the introduction text."); }
  };

  const waiting = data && (data.matchingStatus === "PENDING" || data.matchingStatus === "PROCESSING");
  const timedOut = waiting && Date.now() >= deadlineRef.current;

  return <>
    <h1>Your connections</h1>
    <p>Recommendations are private to your attendee session. Introductions stay here until you choose to copy them.</p>
    {loading && <p role="status" aria-live="polite">Loading your recommendations…</p>}
    {error && <section className="space-y-3"><ErrorNotice error={error} /><button type="button" onClick={() => void refresh()}>Refresh recommendations</button></section>}
    {!loading && !error && waiting && <section className="space-y-3 rounded-xl border bg-white p-5" aria-live="polite">
      <h2>{data.matchingStatus === "PENDING" ? "Preparing your recommendations" : "Finding your connections"}</h2>
      <p>Matching is still in progress. This page checks automatically for up to 45 seconds.</p>
      {timedOut && <p role="status">Automatic checking has stopped. You can refresh when you’re ready.</p>}
      <button type="button" onClick={() => void refresh()}>Refresh now</button>
    </section>}
    {!loading && !error && data?.matchingStatus === "FAILED" && <section role="status" className="space-y-3 rounded-xl border bg-white p-5">
      <h2>We couldn’t prepare recommendations</h2>
      <p>{data.failureCode ? `Reference: ${data.failureCode}` : "Your registration is safe. You can try again."}</p>
      <button type="button" onClick={() => void refresh()}>Refresh status</button>
      {data.retryable && <button type="button" onClick={() => void retry()} disabled={retrying}>{retrying ? "Retrying…" : "Try matching again"}</button>}
    </section>}
    {!loading && !error && data?.matchingStatus === "READY" && <>
      {data.generationMode === "template" && <p role="note" className="rounded-lg border border-amber-300 bg-amber-50 p-4">These recommendations use a fallback template. Introductions will be labeled when generated.</p>}
      <div className="flex flex-wrap items-center justify-between gap-3"><p role="status">{data.matches.length} of up to 3 recommendations</p><button type="button" onClick={() => void refresh()}>Refresh recommendations</button></div>
      {data.matches.length === 0 ? <section className="rounded-xl border bg-white p-5"><h2>No connections yet</h2><p>There aren’t enough eligible attendees for recommendations right now. Check back later.</p></section> : <ul className="space-y-4" aria-label="Recommended connections">
        {data.matches.slice(0, 3).map(match => <li key={match.attendeeId} className="space-y-3 rounded-xl border bg-white p-5 shadow-sm">
          <h2>{match.name}</h2><p className="text-slate-700">{match.jobTitle}{match.company ? ` · ${match.company}` : ""}</p>
          {match.interests.length > 0 && <p className="text-sm text-slate-700">Shared interests: {match.interests.join(", ")}</p>}
          <p>{match.explanation}</p><p className="text-sm text-slate-600"><span className="font-semibold">Conversation starter:</span> {match.conversationStarter}</p>
          <button type="button" onClick={event => void openIntroduction(match, event.currentTarget)}>Prepare an introduction</button>
        </li>)}
      </ul>}
    </>}
    <dialog ref={dialogRef} aria-labelledby="introduction-title" onCancel={event => { event.preventDefault(); closeDialog(); }} onClick={event => { if (event.target === dialogRef.current) closeDialog(); }} className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-xl p-0 backdrop:bg-slate-950/50">
      <section className="space-y-4 p-6">
        <h2 id="introduction-title">Introduction{selected ? ` to ${selected.name}` : ""}</h2>
        <p className="text-sm text-slate-600">This is a draft for you to review. Nothing will be sent.</p>
        {introBusy && <p role="status">Preparing your introduction…</p>}
        {introError && <p role="alert" className="text-red-800">{introError}</p>}
        {introduction && <>
          {introduction.generationMode === "template" && <p role="note" className="rounded border border-amber-300 bg-amber-50 p-3">This introduction uses a fallback template.</p>}
          <label className="block space-y-1 font-medium" htmlFor="introduction-text">Edit your introduction</label>
          <textarea id="introduction-text" aria-label="Edit your introduction" maxLength={600} value={introduction.text} onChange={event => setIntroduction({ ...introduction, text: event.target.value })} />
          <button type="button" onClick={() => void copyIntroduction()} disabled={!introduction.text.trim()}> {copied ? "Copied" : "Copy introduction"}</button>
        </>}
        <button type="button" className="bg-slate-700" onClick={closeDialog}>Close</button>
      </section>
    </dialog>
  </>;
}
