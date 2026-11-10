import { test, expect } from "@playwright/test";
import jsQR from "jsqr";
import { PNG } from "pngjs";

const id = "11111111-1111-4111-8111-111111111111";
const registrationPath = `/conferences/${id}/register`;
const fixture = "http://127.0.0.1:8189";
test.beforeEach(async ({ request }) => { await request.post(`${fixture}/__reset`, { data: {} }); });

test("home is the attendee directory and links each listed conference to registration", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Find your next conference" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Cloud Native Connections" })).toBeVisible();
  await expect(page.getByText("Meet fellow builders")).toBeVisible();
  await expect(page.getByText("Denver")).toBeVisible();
  await expect(page.getByRole("link", { name: "Register" })).toHaveAttribute("href", registrationPath);
  await expect(page.getByRole("link", { name: "Create conference" })).toHaveAttribute("href", "/conferences/new");
  await expect(page.getByRole("link", { name: "Organizer tools" })).toHaveAttribute("href", "/organizer");
});

test("directory handles loading failure with retry and an empty result", async ({ page, request }) => {
  await request.post(`${fixture}/__reset`, { data: { failDirectory: true } });
  await page.goto("/");
  await expect(page.getByRole("status")).toBeVisible();
  await expect(page.getByRole("alert")).toContainText("temporarily unavailable");
  await request.post(`${fixture}/__reset`, { data: { conferences: [] } });
  await page.getByRole("button", { name: "Retry loading conferences" }).click();
  await expect(page.getByRole("heading", { name: "No conferences are open for registration yet" })).toBeVisible();
});

test("creation validates dates, renders a decodable public QR, and forgets the private token", async ({ page, request }) => {
  await page.goto("/conferences/new");
  await page.getByLabel("Conference name").fill("Cloud Native Connections");
  await page.getByLabel("Description").fill("Meet fellow builders");
  await page.getByLabel("Location").fill("Denver");
  await page.getByLabel("Start date and time").fill("2026-11-09T10:00");
  await page.getByLabel("End date and time").fill("2026-11-08T10:00");
  await page.getByRole("button", { name: "Create conference" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Check the conference dates");
  expect((await (await request.get(`${fixture}/__state`)).json()).creations).toHaveLength(0);
  await page.getByLabel("End date and time").fill("2026-11-10T10:00");
  await page.getByRole("button", { name: "Create conference" }).click();
  await expect(page.getByLabel("Private organizer token")).toHaveValue("fixture-private-token");
  const qr = page.getByAltText("Attendee registration QR code");
  await expect(qr).toBeVisible();
  const src = await qr.getAttribute("src");
  const png = PNG.sync.read(Buffer.from(src!.split(",")[1], "base64"));
  const decoded = jsQR(new Uint8ClampedArray(png.data), png.width, png.height);
  expect(decoded?.data).toBe(`http://127.0.0.1:3100${registrationPath}`);
  expect(decoded?.data).not.toContain("fixture-private-token");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.reload();
  await expect(page.getByLabel("Private organizer token")).toHaveCount(0);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
});

test("organizer token uses authorization, supports count refresh, and clears after reload", async ({ page }) => {
  await page.goto("/organizer");
  await page.getByLabel("Conference ID", { exact: true }).fill(id);
  await page.getByLabel("Private organizer token").fill("wrong-token");
  await page.getByRole("button", { name: "View / refresh attendee count" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("denied");
  await page.getByLabel("Private organizer token").fill("fixture-private-token");
  await page.getByRole("button", { name: "View / refresh attendee count" }).click();
  await expect(page.getByText("Registered attendees: 6")).toBeVisible();
  expect(page.url()).not.toContain("token");
  await page.getByRole("button", { name: "View / refresh attendee count" }).click();
  await expect(page.getByText("Registered attendees: 6")).toBeVisible();
  await page.reload();
  await expect(page.getByLabel("Private organizer token")).toHaveValue("");
});

test("attendee validates interests, reuses retry key, reaches confirmation, and receives a proxied HttpOnly cookie", async ({ page, request, context }) => {
  await request.post(`${fixture}/__reset`, { data: { failNext: true } });
  await page.goto(registrationPath);
  await page.getByLabel("Full name").fill(" Alex Chen ");
  await page.getByLabel("Email", { exact: true }).fill("alex@example.test");
  await page.getByLabel("Job title").fill("Platform Engineer");
  await page.getByLabel("Company").fill("Example Labs");
  await page.getByLabel("Professional bio").fill("Building developer platforms");
  await page.getByLabel("Interests").fill(", ,");
  await page.getByLabel("Networking goals").fill("Meet fellow builders");
  await page.getByRole("button", { name: "Register and continue" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Check your interests");
  await page.getByLabel("Interests").fill(" Kubernetes, OpenTelemetry, Kubernetes ");
  await page.getByRole("button", { name: "Register and continue" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("temporarily unavailable");
  await expect(page.getByRole("main").getByRole("alert")).toContainText("fixture-trace");
  await page.getByRole("button", { name: "Register and continue" }).click();
  await expect(page).toHaveURL("/connections");
  await expect(page.getByRole("heading", { name: "You’re registered" })).toBeVisible();
  const state = await (await request.get(`${fixture}/__state`)).json();
  expect(state.registrations).toHaveLength(2);
  expect(state.registrations[0].key).toMatch(/^[0-9a-f-]{36}$/);
  expect(state.registrations[0].key).toBe(state.registrations[1].key);
  expect(state.registrations[1].body.name).toBe("Alex Chen");
  expect(state.registrations[1].body.interests).toEqual(["Kubernetes", "OpenTelemetry"]);
  const cookie = (await context.cookies("http://127.0.0.1:3100/api/v1/me/matches")).find(c => c.name === "wingman_attendee");
  expect(cookie?.httpOnly).toBe(true);
  expect(cookie?.path).toBe("/api/v1");
  expect(await page.evaluate(async () => (await (await fetch("/api/v1/me/matches")).json()).hasCookie)).toBe(true);
});

test("missing public conference gives a usable error", async ({ page }) => {
  await page.goto("/conferences/00000000-0000-4000-8000-000000000000/register");
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Conference not found");
  await expect(page.getByRole("button", { name: "Try loading again" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Register and continue" })).toHaveCount(0);
});
