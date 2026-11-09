import Link from "next/link";
export default function Home() {
  return <><p className="font-semibold text-blue-800">Conference Wingman</p><h1>A first hello starts here.</h1><p>Create a conference and share its registration link so attendees can tell you about themselves.</p><Link className="button" href="/conferences/new">Create a conference</Link><p>Already organizing? <Link href="/organizer">Enter your private organizer token</Link>.</p><p className="text-slate-600">Attending? Open the registration link or scan the QR code shared by your organizer.</p></>;
}
