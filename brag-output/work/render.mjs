// Рендер кадров: node render.mjs stills 0.5 2.9 ... | node render.mjs video
import { createRequire } from "node:module";
const { chromium } = createRequire(import.meta.url)("/opt/node-tools/node_modules/playwright");
import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const [mode, ...rest] = process.argv.slice(2);
const FPS = 30, DURATION = 21;
// FORMAT=vertical — 1080×1920; OUT — куда класть кадры и видео без звука.
const VERT = process.env.FORMAT === "vertical";
const [W, H] = VERT ? [1080, 1920] : [1920, 1080];
const outDir = process.env.OUT ? path.resolve(process.env.OUT) : here;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
page.on("console", (m) => console.log("[page]", m.text()));
page.on("pageerror", (e) => console.log("[pageerror]", e.message));
await page.goto("file://" + path.join(here, "index.html") + (VERT ? "?f=vertical" : ""));
await page.waitForFunction(() => window.ready === true || typeof ready !== "undefined" && ready);

async function frame(t) {
  await page.evaluate((tt) => render(tt), t);
  return page.screenshot({ type: "png" });
}

if (mode === "stills") {
  const fs = await import("node:fs");
  fs.mkdirSync(path.join(outDir, "stills"), { recursive: true });
  for (const s of rest) {
    const buf = await frame(parseFloat(s));
    fs.writeFileSync(path.join(outDir, "stills", `t${parseFloat(s).toFixed(2)}.png`), buf);
  }
} else {
  const out = path.join(outDir, "video-noaudio.mp4");
  const ff = spawn("ffmpeg", ["-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", String(FPS), "-i", "-",
    "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p", "-r", String(FPS), out], { stdio: ["pipe", "inherit", "inherit"] });
  const n = FPS * DURATION;
  for (let i = 0; i < n; i++) {
    // Кадр 0 — постер (лучший устоявшийся кадр), длительность и синхрон не меняются.
    const buf = await frame(i === 0 && process.env.POSTER ? parseFloat(process.env.POSTER) : i / FPS);
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once("drain", r));
    if (i % 60 === 0) console.log(`frame ${i}/${n}`);
  }
  ff.stdin.end();
  await new Promise((r) => ff.on("close", r));
  console.log("wrote", out);
}
await browser.close();
