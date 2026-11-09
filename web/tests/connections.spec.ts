import { expect, test } from "@playwright/test";

const fixture = "http://127.0.0.1:8189";
const attendee = { attendeeId: "33333333-3333-4333-8333-333333333333", name: "Jordan Rivera", jobTitle: "SRE", company: "Sample Systems", interests: ["OpenTelemetry"], explanation: "You both work on observability and developer experience.", conversationStarter: "What has helped your team make tracing easier to adopt?" };
const ready = { registrationId: "22222222-2222-4222-8222-222222222222", matchingStatus: "READY", generationMode: "llm", matches: [attendee], availableMatchCount: 1, retryable: false };

test.beforeEach(async ({ request }) => { await request.post(`${fixture}/__reset`, { data: {} }); });

test("shows private recommendations and an editable copy-only introduction dialog", async ({ page, request }) => {
  await page.goto("/connections");
  await expect(page.getByRole("heading", { name: "Jordan Rivera" })).toBeVisible();
  await expect(page.getByText(attendee.explanation)).toBeVisible();
  await expect(page.getByText(attendee.conversationStarter)).toBeVisible();
  await expect(page.getByText(/@/)).toHaveCount(0);

  const opener = page.getByRole("button", { name: "Prepare an introduction" });
  await opener.click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText("Nothing will be sent.")).toBeVisible();
  const text = dialog.getByRole("textbox", { name: "Edit your introduction" });
  await expect(text).toHaveValue(/Hi Jordan/);
  await text.fill("Hello Jordan, edited by me.");
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
  await dialog.getByRole("button", { name: "Copy introduction" }).click();
  await expect(dialog.getByRole("button", { name: "Copied" })).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe("Hello Jordan, edited by me.");
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  await expect(opener).toBeFocused();
  expect((await request.get(`${fixture}/__state`).then(response => response.json())).introductions).toEqual([{ targetAttendeeId: attendee.attendeeId }]);
});

test("ignores a late introduction response after closing and reopening for another attendee", async ({ page }) => {
  const secondAttendee = { ...attendee, attendeeId: "44444444-4444-4444-8444-444444444444", name: "Casey Morgan" };
  await page.route("**/api/v1/me/matches", route => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ ...ready, matches: [attendee, secondAttendee], availableMatchCount: 2 }),
  }));

  let releaseFirst!: () => void;
  let releaseSecond!: () => void;
  const firstResponse = new Promise<void>(resolve => { releaseFirst = resolve; });
  const secondResponse = new Promise<void>(resolve => { releaseSecond = resolve; });
  await page.route("**/api/v1/me/introductions", async route => {
    const { targetAttendeeId } = route.request().postDataJSON();
    await (targetAttendeeId === attendee.attendeeId ? firstResponse : secondResponse);
    const target = targetAttendeeId === attendee.attendeeId ? attendee : secondAttendee;
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ targetAttendeeId, text: `Hi ${target.name}, nice to meet you.`, generationMode: "llm", delivery: "copy-only" }),
    });
  });

  await page.goto("/connections");
  const openers = page.getByRole("button", { name: "Prepare an introduction" });
  await openers.nth(0).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("status")).toHaveText("Preparing your introduction…");
  await dialog.getByRole("button", { name: "Close" }).click();

  await openers.nth(1).click();
  await expect(dialog.getByRole("heading", { name: "Introduction to Casey Morgan" })).toBeVisible();
  await expect(dialog.getByRole("status")).toHaveText("Preparing your introduction…");

  const firstRequestResponse = page.waitForResponse(response => response.url().includes("/api/v1/me/introductions") && response.request().postData()?.includes(attendee.attendeeId) === true);
  releaseFirst();
  await firstRequestResponse;
  await expect(dialog.getByRole("status")).toHaveText("Preparing your introduction…");
  await expect(dialog.getByRole("textbox", { name: "Edit your introduction" })).toHaveCount(0);

  releaseSecond();
  await expect(dialog.getByRole("textbox", { name: "Edit your introduction" })).toHaveValue("Hi Casey Morgan, nice to meet you.");
});

test("labels degraded output and presents the valid empty state", async ({ page, request }) => {
  await request.post(`${fixture}/__reset`, { data: { matches: { ...ready, generationMode: "template", matches: [], availableMatchCount: 0 } } });
  await page.goto("/connections");
  await expect(page.getByRole("note")).toContainText("fallback template");
  await expect(page.getByRole("heading", { name: "No connections yet" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Refresh recommendations" })).toBeVisible();
});

test("failed matching can be retried and manual refresh reports unavailable service", async ({ page, request }) => {
  await request.post(`${fixture}/__reset`, { data: { matches: { ...ready, matchingStatus: "FAILED", matches: [], retryable: true } } });
  await page.goto("/connections");
  await expect(page.getByRole("heading", { name: "We couldn’t prepare recommendations" })).toBeVisible();
  await page.getByRole("button", { name: "Try matching again" }).click();
  await expect(page.getByText("Matching is still in progress.")).toBeVisible();

  await request.post(`${fixture}/__reset`, { data: { failMatches: true } });
  await page.reload();
  await expect(page.locator("main [role=alert]")).toContainText("temporarily unavailable");
  await page.getByRole("button", { name: "Refresh recommendations" }).click();
  await expect(page.locator("main [role=alert]")).toBeVisible();
});

test("stops automatic polling at the 45 second limit", async ({ page, request }) => {
  await request.post(`${fixture}/__reset`, { data: { matches: { ...ready, matchingStatus: "PROCESSING", matches: [], pollAfterSeconds: 2 } } });
  await page.clock.install();
  await page.goto("/connections");
  await expect(page.getByText("This page checks automatically for up to 45 seconds.")).toBeVisible();
  await page.clock.fastForward(46_000);
  await expect(page.getByText("Automatic checking has stopped.")).toBeVisible();
});
