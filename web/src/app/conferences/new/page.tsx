"use client";
import { useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { api, ApiError, type Created } from "../../../lib/api";
import { Field, ErrorNotice, asError } from "../../../components/Forms";
import RegistrationLink from "../../../components/RegistrationLink";

export default function CreateConference() {
  const [created, setCreated] = useState<Created | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending.current) return;
    const form = new FormData(event.currentTarget);
    const text = (key: string) => String(form.get(key) || "").trim();
    const start = new Date(text("startDate")), end = new Date(text("endDate"));
    if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end < start) {
      setError(new ApiError(400, "Check the conference dates.", { endDate: "End must be on or after start." })); return;
    }
    pending.current = true; setBusy(true); setError(null);
    try { setCreated(await api<Created>("/conferences", { method: "POST", body: JSON.stringify({ name: text("name"), description: text("description"), location: text("location"), startDate: start.toISOString(), endDate: end.toISOString() }) })); }
    catch (e) { setError(asError(e)); } finally { pending.current = false; setBusy(false); }
  }
  if (created) return <><h1>Conference created</h1><p>{created.conference.name}</p><section className="space-y-3 rounded-xl border border-amber-400 bg-amber-50 p-5"><h2>Save your private organizer token</h2><p>Shown only now. Copy it somewhere private before leaving. It cannot be recovered.</p><label className="block" htmlFor="created-token">Private organizer token</label><textarea id="created-token" readOnly value={created.organizerToken} spellCheck={false} /><p className="break-all">Conference ID: <strong>{created.conference.id}</strong></p></section><RegistrationLink url={created.registrationUrl} /><Link href="/organizer">Open organizer view with your saved ID and token</Link></>;
  return <><h1>Create a conference</h1><p>Give attendees a place to start meeting fellow builders.</p><ErrorNotice error={error} /><form onSubmit={submit} className="space-y-5"><fieldset disabled={busy} className="space-y-5"><Field label="Conference name" name="name" maxLength={120} error={error?.fields.name} /><Field label="Description" name="description" multiline maxLength={2000} error={error?.fields.description} /><Field label="Location" name="location" maxLength={200} error={error?.fields.location} /><p className="text-sm text-slate-600">Dates use your device’s local time zone.</p><div className="grid gap-5 sm:grid-cols-2"><Field label="Start date and time" name="startDate" type="datetime-local" error={error?.fields.startDate} /><Field label="End date and time" name="endDate" type="datetime-local" error={error?.fields.endDate} /></div><button type="submit" disabled={busy}>{busy ? "Creating…" : "Create conference"}</button></fieldset></form></>;
}
