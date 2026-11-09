"use client";
import { useEffect, useState } from "react";
import QRCode from "qrcode";

export default function RegistrationLink({ url }: { url: string }) {
  const [image, setImage] = useState("");
  useEffect(() => { let active = true; setImage(""); QRCode.toDataURL(url, { width: 280, margin: 4, errorCorrectionLevel: "M" }).then(value => { if (active) setImage(value); }).catch(() => {}); return () => { active = false; }; }, [url]);
  return <section className="space-y-3 rounded-xl border bg-white p-5"><h2>Share attendee registration</h2><a className="block break-all" href={url}>{url}</a>{image ? <img src={image} width={280} height={280} className="max-w-full" alt="Attendee registration QR code" /> : <p>QR unavailable or loading. Use the registration link above.</p>}<p className="text-sm text-slate-600">This public link contains no organizer token. QR codes are generated on this device. A localhost link works only on this computer.</p></section>;
}
