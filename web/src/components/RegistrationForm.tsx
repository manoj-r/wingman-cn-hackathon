"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError, conferencePath, registrationPayload, type Conference } from "../lib/api";
import { Field, ErrorNotice, asError } from "./Forms";

export default function RegistrationForm({ conferenceId }: { conferenceId: string }) {
  const router = useRouter();
  const [conference, setConference] = useState<Conference | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const [load, setLoad] = useState(0);
  const pending = useRef(false);
  const attempt = useRef<{ body: string; key: string } | null>(null);
  useEffect(() => { let active = true; setError(null); setConference(null); api<Conference>(conferencePath(conferenceId)).then(value => { if (active) setConference(value); }).catch(e => { if (active) setError(asError(e)); }); return () => { active = false; }; }, [conferenceId, load]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending.current) return;
    const payload = registrationPayload(new FormData(event.currentTarget));
    if (payload.interests.length < 1 || payload.interests.length > 10 || payload.interests.some(i => i.length > 60)) {
      setError(new ApiError(400, "Check your interests.", { interests: "Enter 1–10 distinct interests, each at most 60 characters." })); return;
    }
    const body = JSON.stringify(payload);
    if (!attempt.current || attempt.current.body !== body) attempt.current = { body, key: crypto.randomUUID() };
    pending.current = true; setBusy(true); setError(null);
    try {
      await api<{ registrationId: string; connectionsPath: string }>(`${conferencePath(conferenceId)}/registrations`, { method: "POST", headers: { "Idempotency-Key": attempt.current.key }, body });
      router.push("/connections");
    } catch (e) { setError(asError(e)); } finally { pending.current = false; setBusy(false); }
  }
  if (!conference) return <><h1>Attendee registration</h1><ErrorNotice error={error} />{error ? <button onClick={() => setLoad(load + 1)}>Try loading again</button> : <p role="status">Loading conference…</p>}</>;
  return <><h1>Register for {conference.name}</h1><p>{conference.description}</p><p>{conference.location} · {new Date(conference.startDate).toLocaleString()} – {new Date(conference.endDate).toLocaleString()}</p><p>Tell us about your work and who you’d like to meet. All fields are required.</p><ErrorNotice error={error} /><form onSubmit={submit} className="space-y-5"><fieldset disabled={busy} className="space-y-5"><Field name="name" label="Full name" maxLength={120} autoComplete="name" error={error?.fields.name} /><Field name="email" label="Email" type="email" maxLength={254} autoComplete="email" error={error?.fields.email} /><div className="grid gap-5 sm:grid-cols-2"><Field name="jobTitle" label="Job title" maxLength={160} autoComplete="organization-title" error={error?.fields.jobTitle} /><Field name="company" label="Company" maxLength={160} autoComplete="organization" error={error?.fields.company} /></div><Field name="bio" label="Professional bio" multiline maxLength={2000} error={error?.fields.bio} /><Field name="interests" label="Interests (comma separated, 1–10)" error={error?.fields.interests} /><Field name="networkingGoals" label="Networking goals" multiline maxLength={1000} error={error?.fields.networkingGoals} /><p className="text-sm text-slate-600">Your email is not shared in recommendations. This browser will hold your private attendee session for 24 hours; registering again replaces it.</p><button disabled={busy}>{busy ? "Registering…" : "Register and continue"}</button></fieldset></form></>;
}
