"use client";
import { useRef, useState, type FormEvent } from "react";
import { api, ApiError, conferencePath, type Management } from "../../lib/api";
import { Field, ErrorNotice, asError } from "../../components/Forms";
import RegistrationLink from "../../components/RegistrationLink";

export default function Organizer() {
  const [data, setData] = useState<Management | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending.current) return;
    const form = new FormData(event.currentTarget);
    setData(null); setError(null); setBusy(true); pending.current = true;
    try { setData(await api<Management>(`${conferencePath(String(form.get("conferenceId")))}/management`, { headers: { Authorization: `Bearer ${String(form.get("token")).trim()}` } })); }
    catch (e) { setError(asError(e)); } finally { setBusy(false); pending.current = false; }
  }
  return <><h1>Organizer view</h1><p>Enter the ID and private token saved when you created the conference. The token stays in this tab; reload to clear it.</p><ErrorNotice error={error} /><form onSubmit={submit} className="space-y-5" autoComplete="off"><fieldset disabled={busy} className="space-y-5"><Field label="Conference ID" name="conferenceId" pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" /><Field label="Private organizer token" name="token" type="password" autoComplete="off" /><button disabled={busy}>{busy ? "Loading…" : "View / refresh attendee count"}</button></fieldset></form>{data && <><section aria-live="polite" className="space-y-3"><h2>{data.conference.name}</h2><p className="text-2xl font-semibold">Registered attendees: {data.registeredAttendeeCount}</p></section><RegistrationLink url={data.registrationUrl} /></>}</>;
}
