export type Conference = { id: string; name: string; description: string; location: string; startDate: string; endDate: string };
export type Management = { conference: Conference; registrationUrl: string; registeredAttendeeCount: number };
export type Created = { conference: Conference; registrationUrl: string; organizerToken: string };
export class ApiError extends Error {
  constructor(public status: number, message: string, public fields: Record<string, string> = {}, public traceId?: string, public retryAfter?: string) { super(message); }
}
const messages: Record<number, string> = {
  400: "Check the highlighted fields.", 401: "Organizer access was denied. Check the conference ID and private token.",
  404: "Conference not found. Check the link or conference ID.",
  409: "This registration conflicts with an existing submission. Check your details before trying again.",
  429: "Too many requests. Please wait before trying again.", 503: "The service is temporarily unavailable. Please try again.",
};
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, { ...init, credentials: "same-origin", cache: "no-store", signal: AbortSignal.timeout(20000), headers: { "Content-Type": "application/json", ...init.headers } });
  } catch { throw new ApiError(0, "Could not reach the service. Check your connection and try again."); }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(response.status, messages[response.status] || "The request could not be completed. Please try again.", body?.error?.fields || {}, body?.error?.traceId, response.headers.get("Retry-After") || undefined);
  }
  if (!body) throw new ApiError(0, "The service returned an unexpected response. Please try again.");
  return body as T;
}
export const conferencePath = (id: string) => `/conferences/${encodeURIComponent(id.trim())}`;
export function registrationPayload(form: FormData) {
  const text = (key: string) => String(form.get(key) || "").trim();
  return { name: text("name"), email: text("email"), jobTitle: text("jobTitle"), company: text("company"), bio: text("bio"), networkingGoals: text("networkingGoals"), interests: [...new Set(text("interests").split(",").map(s => s.trim()).filter(Boolean))] };
}
