#!/usr/bin/env node
/**
 * Post-migration content verification gate.
 *
 * Run after `./gradlew migrate` (npm run check:content). Two checks:
 *
 * 1. MDX PARSE (fails the run): compiles every src/content/docs page with the
 *    real MDX compiler. Catches converter output that would crash the site at
 *    request time (e.g. a raw `<1>` conum leaking into body text) — without
 *    needing a full `astro build`.
 *
 * 2. CALLOUT PAIRING (reported, non-fatal): every <CalloutList> is checked
 *    against the callout markers actually available in the nearest preceding
 *    code source (fence, <SampleScripts>/<SampleFile> snippet region, or
 *    <Tabs> block). Mismatches are usually upstream sources attaching the
 *    explanation list to a console-output block instead of the annotated
 *    code (renders fine, reads awkwardly) — but a NEW mismatch after a
 *    migrate is worth a look before committing.
 */
import { compile } from "@mdx-js/mdx";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const docsRoot = join(root, "src/content/docs");
const samplesRoot = join(root, "src/samples");

function* mdxFiles(dir) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) yield* mdxFiles(p);
    else if (entry.endsWith(".mdx")) yield p;
  }
}

// --- check 1: MDX parse ----------------------------------------------------

async function checkParse(files) {
  const errors = [];
  for (const file of files) {
    try {
      await compile(readFileSync(file, "utf8"), { format: "mdx" });
    } catch (err) {
      const loc = err.line ? `${err.line}:${err.column ?? 0}` : "?";
      errors.push(`${relative(root, file)}:${loc}  ${err.reason ?? err.message}`);
    }
  }
  return errors;
}

// --- check 2: callout pairing ----------------------------------------------

function tagRegion(content, tagspec) {
  if (!tagspec) return content;
  const wanted = new Set(
    tagspec
      .split(/[;,]/)
      .map((t) => t.trim())
      .filter((t) => t && !t.startsWith("!")),
  );
  const out = [];
  let active = 0;
  for (const line of content.split("\n")) {
    const m = line.match(/\b(tag|end)::([\w.-]+)\[\]/);
    if (m) {
      if (wanted.has(m[2])) active += m[1] === "tag" ? 1 : -1;
      continue;
    }
    if (active > 0) out.push(line);
  }
  return out.join("\n");
}

/** Marker numbers in text; `<.>`/`(.)` auto-numbers resolve sequentially. */
function markersOf(text, atEol = true) {
  const pat = atEol
    ? /(?:[(<](\d+|\.)[)>]|<!--\s*(\d+|\.)\s*-->)\s*$/gm
    : /[(<](\d+|\.)[)>]|<!--\s*(\d+|\.)\s*-->/g;
  const found = new Set();
  let auto = 0;
  for (const m of text.matchAll(pat)) {
    const tok = m[1] ?? m[2];
    if (tok === ".") found.add(++auto);
    else {
      const n = Number(tok);
      found.add(n);
      auto = Math.max(auto, n);
    }
  }
  return found;
}

function sampleMarkers(page, tagLine) {
  const got = new Set();
  const name = tagLine.match(/sample(?:_name)?="([^"]+)"/);
  if (!name) return got;
  const tag = tagLine.match(/tag="([^"]+)"/);
  const dir = join(samplesRoot, page, name[1]);
  const walk = (d) => {
    let entries;
    try {
      entries = readdirSync(d);
    } catch {
      return;
    }
    for (const e of entries) {
      const p = join(d, e);
      if (statSync(p).isDirectory()) walk(p);
      else {
        let c;
        try {
          c = readFileSync(p, "utf8");
        } catch {
          continue;
        }
        for (const n of markersOf(tagRegion(c, tag ? tag[1] : null))) got.add(n);
      }
    }
  };
  walk(dir);
  return got;
}

function checkCallouts(files) {
  const mismatches = [];
  for (const file of files) {
    const page = relative(docsRoot, dirname(file));
    const lines = readFileSync(file, "utf8").split("\n");
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].trim() !== "<CalloutList>") continue;
      const needed = new Set();
      for (let j = i + 1; j < lines.length && lines[j].trim() !== "</CalloutList>"; j++) {
        const m = lines[j].trim().match(/^(\d+)\./);
        if (m) needed.add(Number(m[1]));
      }
      if (needed.size === 0) continue;
      let k = i - 1;
      while (k >= 0 && lines[k].trim() === "") k--;
      let avail = new Set();
      if (k >= 0 && lines[k].trim() === "```") {
        let fs = k - 1;
        while (fs >= 0 && !lines[fs].startsWith("```")) fs--;
        avail = markersOf(lines.slice(fs + 1, k).join("\n"), false);
      } else if (k >= 0 && /^<Sample(Scripts|File)\s/.test(lines[k].trim())) {
        avail = sampleMarkers(page, lines[k]);
      } else if (k >= 0 && ["</Tabs>", "</TabItem>"].includes(lines[k].trim())) {
        let ts = k;
        while (ts >= 0 && !lines[ts].trim().startsWith("<Tabs")) ts--;
        const block = lines.slice(Math.max(ts, 0), k + 1);
        avail = markersOf(block.filter((l) => !l.startsWith("<")).join("\n"), false);
        for (const bl of block) {
          if (/^\s*<Sample(Scripts|File)\s/.test(bl))
            for (const n of sampleMarkers(page, bl)) avail.add(n);
        }
      }
      const missing = [...needed].filter((n) => !avail.has(n)).sort((a, b) => a - b);
      if (missing.length)
        mismatches.push(
          `${page}  list expects [${missing.join(", ")}] not found in preceding code`,
        );
    }
  }
  return mismatches;
}

// --- run ---------------------------------------------------------------------

const files = [...mdxFiles(docsRoot)];
console.log(`Checking ${files.length} MDX files…`);

const parseErrors = await checkParse(files);
const calloutIssues = checkCallouts(files);

if (calloutIssues.length) {
  console.log(
    `\nCallout pairing warnings (${calloutIssues.length}) — usually a displaced explanation list in the source; review anything new:`,
  );
  for (const c of calloutIssues) console.log("  " + c);
}

if (parseErrors.length) {
  console.error(`\nMDX PARSE ERRORS (${parseErrors.length}) — these pages will crash the site:`);
  for (const e of parseErrors) console.error("  " + e);
  process.exit(1);
}

console.log(
  `\nOK: all ${files.length} pages parse. ${calloutIssues.length} callout pairing warning(s).`,
);
