// Contract fixture only: no real persistence or matching is claimed by these tests.
import { createServer } from "node:http";
const id = "11111111-1111-4111-8111-111111111111";
const conference = { id, name: "Cloud Native Connections", description: "Meet fellow builders", location: "Denver", startDate: "2026-11-09T16:00:00Z", endDate: "2026-11-10T01:00:00Z" };
const registrationUrl = `http://127.0.0.1:3100/conferences/${id}/register`;
let state = { registrations: [], creations: [], failNext: false };
createServer(async (req, res) => {
  let raw = ""; for await (const part of req) raw += part;
  const body = raw ? JSON.parse(raw) : null;
  const send = (status, data, headers = {}) => { res.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store", ...headers }); res.end(JSON.stringify(data)); };
  if (req.url === "/healthz") return send(200, {});
  if (req.url === "/__reset") { state = { registrations: [], creations: [], failNext: body?.failNext || false }; return send(200, {}); }
  if (req.url === "/__state") return send(200, state);
  if (req.url === "/api/v1/conferences" && req.method === "POST") { state.creations.push(body); return send(201, { conference: { ...conference, ...body }, registrationUrl, organizerToken: "fixture-private-token" }); }
  if (req.url === `/api/v1/conferences/${id}`) return send(200, conference);
  if (req.url === `/api/v1/conferences/${id}/management`) {
    if (req.headers.authorization !== "Bearer fixture-private-token") return send(401, { error: { code: "UNAUTHORIZED" } });
    return send(200, { conference, registrationUrl, registeredAttendeeCount: 6 });
  }
  if (req.url === `/api/v1/conferences/${id}/registrations` && req.method === "POST") {
    state.registrations.push({ body, key: req.headers["idempotency-key"] });
    if (state.failNext) { state.failNext = false; return send(503, { error: { code: "UNAVAILABLE", traceId: "fixture-trace" } }, { "Retry-After": "1" }); }
    if (!req.headers["idempotency-key"]) return send(400, { error: { code: "VALIDATION_ERROR" } });
    return send(201, { registrationId: "22222222-2222-4222-8222-222222222222", conferenceId: id, matchingStatus: "PENDING", connectionsPath: "/connections" }, { "Set-Cookie": "wingman_attendee=fixture-session; HttpOnly; SameSite=Lax; Path=/api/v1; Max-Age=86400" });
  }
  if (req.url === "/api/v1/me/matches") return send(200, { hasCookie: req.headers.cookie?.includes("wingman_attendee=fixture-session") || false });
  return send(404, { error: { code: "NOT_FOUND" } });
}).listen(8189, "127.0.0.1");
