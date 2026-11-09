import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = { title: "Conference Wingman", description: "Meet fellow conference builders." };
export default function Layout({ children }: { children: React.ReactNode }) {
  return <html lang="en"><body><header className="border-b bg-white"><nav aria-label="Main" className="mx-auto flex max-w-3xl flex-wrap gap-5 px-5 py-5"><Link href="/" className="mr-auto font-bold">Wingman</Link><Link href="/conferences/new">Create conference</Link><Link href="/organizer">Organizer</Link></nav></header><main className="mx-auto max-w-3xl space-y-6 px-5 py-10">{children}</main></body></html>;
}
