// Рендер вертикального ролика: node render.mjs stills 1.5 8.2 … | POSTER=<t> node render.mjs video
import { createRequire } from "node:module";
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const { chromium } = createRequire(import.meta.url)("/opt/node-tools/node_modules/playwright");
const here = path.dirname(fileURLToPath(import.meta.url));
const [mode, ...rest] = process.argv.slice(2);
const FPS = 30, DURATION = 21, W = 1080, H = 1920;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
page.on("pageerror", (e) => console.log("[pageerror]", e.message));
await page.goto("file://" + path.join(here, "index.html"));
await page.waitForFunction(() => window.ready === true);

async function frame(t) {
  await page.evaluate((tt) => render(tt), t);
  return page.screenshot({ type: "png" });
}

if (mode === "stills") {
  fs.mkdirSync(path.join(here, "stills"), { recursive: true });
  for (const s of rest) fs.writeFileSync(path.join(here, "stills", `t${parseFloat(s).toFixed(2).padStart(5, "0")}.png`), await frame(parseFloat(s)));
} else {
  const out = path.join(here, "video-noaudio.mp4");
  const ff = spawn("ffmpeg", ["-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", String(FPS), "-i", "-",
    "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p", "-r", String(FPS), out], { stdio: ["pipe", "inherit", "inherit"] });
  for (let i = 0; i < FPS * DURATION; i++) {
    // Кадр 0 — постер (лучший устоявшийся кадр), длительность и синхрон не меняются.
    const buf = await frame(i === 0 && process.env.POSTER ? parseFloat(process.env.POSTER) : i / FPS);
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once("drain", r));
    if (i % 90 === 0) console.log(`frame ${i}`);
  }
  ff.stdin.end();
  await new Promise((r) => ff.on("close", r));
  console.log("wrote", out);
}
await browser.close();
