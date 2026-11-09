"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { api, Conference, conferencePath } from "../lib/api";

function dateRange(start: string, end: string) {
  const format = (value: string) => new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium", timeStyle: "short",
  }).format(new Date(value));
  return `${format(start)} – ${format(end)}`;
}

export default function ConferenceDirectory() {
  const [conferences, setConferences] = useState<Conference[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setConferences(await api<Conference[]>("/conferences"));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load conferences. Please try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return <>
    <p className="font-semibold text-blue-800">Conference Wingman</p>
    <h1>Find your next conference</h1>
    <p>Browse conferences and register to meet fellow builders.</p>
    {loading && <p role="status" aria-live="polite">Loading available conferences…</p>}
    {!loading && error && <section role="alert" className="space-y-3 rounded-lg border border-red-300 bg-red-50 p-5">
      <p>{error}</p>
      <button type="button" onClick={() => void load()}>Retry loading conferences</button>
    </section>}
    {!loading && !error && conferences?.length === 0 && <section className="rounded-lg border bg-white p-5">
      <h2>No conferences are open for registration yet</h2>
      <p>Check back later, or share this page with an organizer.</p>
    </section>}
    {!loading && !error && conferences && conferences.length > 0 && <ul className="space-y-4" aria-label="Available conferences">
      {conferences.map(conference => <li key={conference.id} className="space-y-3 rounded-xl border bg-white p-5 shadow-sm">
        <h2>{conference.name}</h2>
        <p className="text-slate-700">{conference.description}</p>
        <dl className="grid gap-2 text-sm text-slate-700 sm:grid-cols-2">
          <div><dt className="font-semibold">Location</dt><dd>{conference.location}</dd></div>
          <div><dt className="font-semibold">When</dt><dd>{dateRange(conference.startDate, conference.endDate)}</dd></div>
        </dl>
        <Link className="button" href={`${conferencePath(conference.id)}/register`}>Register</Link>
      </li>)}
    </ul>}
  </>;
}
