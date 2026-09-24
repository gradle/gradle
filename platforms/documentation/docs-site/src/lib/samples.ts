import { selectSampleContent } from "./sample-tags";

export interface SampleScript {
  language: "groovy" | "kotlin" | "dcl";
  label: string;
  filename: string;
  content: string;
  syntaxHighlight: string;
}

export interface SampleScriptsResult {
  scripts: SampleScript[];
}

const LANGUAGE_CONFIG = {
  kotlin: {
    label: "Kotlin",
    extension: ".gradle.kts",
    syntaxHighlight: "kotlin",
  },
  groovy: {
    label: "Groovy",
    extension: ".gradle",
    syntaxHighlight: "groovy",
  },
  dcl: {
    label: "DCL",
    extension: ".gradle.dcl",
    syntaxHighlight: "kotlin",
  },
} as const;

const sampleFiles = import.meta.glob("/src/samples/**/*", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

function getSamplePrefix(sample: string): string {
  return `/src/samples/${sample}/`;
}

function normalizePathSegment(path: string): string {
  return path.replace(/^\/+|\/+$/g, "");
}

export function getPageScopedSamplePath(pagePathname: string, sampleName: string): string {
  const normalizedPagePath = normalizePathSegment(pagePathname);
  const normalizedSampleName = normalizePathSegment(sampleName);

  if (!normalizedSampleName) {
    throw new Error("Expected a non-empty sample name.");
  }

  return normalizedPagePath
    ? `${normalizedPagePath}/${normalizedSampleName}`
    : normalizedSampleName;
}

function assertSampleExists(sample: string): void {
  const samplePrefix = getSamplePrefix(sample);
  const sampleExists = Object.keys(sampleFiles).some((key) => key.startsWith(samplePrefix));

  if (!sampleExists) {
    throw new Error(`Sample directory '${sample}' not found in src/samples/`);
  }
}

/**
 * Loads sample scripts for a given sample and path.
 * @param sample - The sample directory name (e.g., 'sample-build')
 * @param path - The file path without extension (e.g., 'build')
 * @param subpath - Optional subpath inside each language directory (e.g., 'app/')
 * @param tag - Optional AsciiDoc tags= selector; displays only the tagged region(s)
 * @returns An object containing the available scripts
 */
export function loadSampleScripts(
  sample: string,
  path: string,
  subpath = "",
  tag?: string,
): SampleScriptsResult {
  const scripts: SampleScript[] = [];
  assertSampleExists(sample);
  const normalizedSubpath = normalizePathSegment(subpath);

  for (const [language, config] of Object.entries(LANGUAGE_CONFIG)) {
    const languageSubpath = normalizedSubpath.replaceAll("{lang}", language);
    const resolvedPath = languageSubpath ? `${languageSubpath}/${path}` : path;
    const filePath = `/src/samples/${sample}/${language}/${resolvedPath}${config.extension}`;
    const content = sampleFiles[filePath];

    if (content !== undefined && content.trim().length > 0) {
      scripts.push({
        language: language as "groovy" | "kotlin" | "dcl",
        label: config.label,
        filename: `${resolvedPath}${config.extension}`,
        content: selectSampleContent(content, tag, filePath),
        syntaxHighlight: config.syntaxHighlight,
      });
    }
  }

  if (scripts.length === 0) {
    const shownPath = normalizedSubpath ? `${normalizedSubpath}/${path}` : path;
    throw new Error(
      `No scripts found for path '${shownPath}' in sample '${sample}'. ` +
        `Expected at least one of: ${shownPath}.gradle, ${shownPath}.gradle.kts, ${shownPath}.gradle.dcl`,
    );
  }

  return { scripts };
}
