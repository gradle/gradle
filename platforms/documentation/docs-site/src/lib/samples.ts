export interface SampleScript {
  language: 'groovy' | 'kotlin' | 'dcl';
  label: string;
  filename: string;
  content: string;
  syntaxHighlight: string;
}

export interface SampleScriptsResult {
  scripts: SampleScript[];
}

const LANGUAGE_CONFIG = {
  groovy: {
    label: 'Groovy',
    extension: '.gradle',
    syntaxHighlight: 'groovy',
  },
  kotlin: {
    label: 'Kotlin',
    extension: '.gradle.kts',
    syntaxHighlight: 'kotlin',
  },
  dcl: {
    label: 'DCL',
    extension: '.gradle.dcl',
    syntaxHighlight: 'kotlin', // DCL syntax is similar to Kotlin
  },
} as const;

// Load all sample files using Vite's glob import
const sampleFiles = import.meta.glob('/src/samples/**/*', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

function getSamplePrefix(sample: string): string {
  return `/src/samples/${sample}/`;
}

function normalizePathSegment(path: string): string {
  return path.replace(/^\/+|\/+$/g, '');
}

export function getPageScopedSamplePath(pagePathname: string, sampleName: string): string {
  const normalizedPagePath = normalizePathSegment(pagePathname);
  const normalizedSampleName = normalizePathSegment(sampleName);

  if (!normalizedSampleName) {
    throw new Error('Expected a non-empty sample name.');
  }

  // The `userguide/` segment is a publishing/routing prefix; samples are keyed by
  // content-relative path, so strip it before resolving.
  const contentRelativePath = normalizedPagePath.replace(/^userguide(\/|$)/, '');

  return contentRelativePath
    ? `${contentRelativePath}/${normalizedSampleName}`
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
 * @returns An object containing the available scripts
 */
export function loadSampleScripts(sample: string, path: string, subpath = ''): SampleScriptsResult {
  const scripts: SampleScript[] = [];
  assertSampleExists(sample);
  const normalizedSubpath = normalizePathSegment(subpath);
  const resolvedPath = normalizedSubpath ? `${normalizedSubpath}/${path}` : path;

  // Check each language variant
  for (const [language, config] of Object.entries(LANGUAGE_CONFIG)) {
    const filePath = `/src/samples/${sample}/${language}/${resolvedPath}${config.extension}`;
    const content = sampleFiles[filePath];

    if (content !== undefined) {
      scripts.push({
        language: language as 'groovy' | 'kotlin' | 'dcl',
        label: config.label,
        filename: `${resolvedPath}${config.extension}`,
        content,
        syntaxHighlight: config.syntaxHighlight,
      });
    }
  }

  // If sample exists but no scripts found for this path
  if (scripts.length === 0) {
    throw new Error(
      `No scripts found for path '${resolvedPath}' in sample '${sample}'. ` +
      `Expected at least one of: ${resolvedPath}.gradle, ${resolvedPath}.gradle.kts, ${resolvedPath}.gradle.dcl`
    );
  }

  return { scripts };
}
