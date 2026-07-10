/**
 * URL builders for the three reference-doc trees that are deployed as siblings
 * of the user guide inside the versioned docs directory (docs.gradle.org/
 * <version>/{javadoc,dsl,kotlin-dsl}). Site-absolute paths; the
 * astro-relative-links pass makes them mount-independent at build time.
 *
 * These are the inverse of the converter's DocLinkResolver parsing
 * (tools/asciidoc-to-mdx), which turned legacy {javadocPath}/{groovyDslPath}/
 * {kotlinDslPath} URLs into <JavaDocLink>/<GroovyDocLink>/<KotlinDocLink>
 * class/method props.
 */

/** `org.gradle.api.Task.Namer` → `/javadoc/org/gradle/api/Task.Namer.html`. */
export function javadocUrl(className: string, method?: string): string {
  const segments = className.split(".");
  // Package segments are lowercase; the first capitalized segment starts the
  // (possibly nested) class name, which javadoc joins with dots in the filename.
  const classStart = segments.findIndex((s) => /^[A-Z]/.test(s));
  const split = classStart === -1 ? segments.length - 1 : classStart;
  const pkg = segments.slice(0, split).join("/");
  const file = segments.slice(split).join(".");
  const path = pkg ? `${pkg}/${file}` : file;
  return `/javadoc/${path}.html${method ? `#${method}` : ""}`;
}

/** `org.gradle.api.Project` + `allprojects(...)` → `/dsl/org.gradle.api.Project.html#org.gradle.api.Project:allprojects(...)`. */
export function groovyDslUrl(className: string, method?: string): string {
  return `/dsl/${className}.html${method ? `#${className}:${method}` : ""}`;
}

/** `org.gradle.api.Project` + `repositories` → `/kotlin-dsl/gradle/org.gradle.api/-project/repositories.html`. */
export function kotlinDslUrl(className: string, method?: string): string {
  const lastDot = className.lastIndexOf(".");
  const pkg = lastDot === -1 ? "" : className.slice(0, lastDot);
  const type = className.slice(lastDot + 1);
  // Dokka's type slug: CamelCase → dash-case with a leading dash (-default-task).
  const dashType = "-" + type.replace(/[A-Z]/g, (c, i) => (i === 0 ? c : `-${c}`).toLowerCase());
  const member = method ? `${method}.html` : "index.html";
  return `/kotlin-dsl/gradle/${pkg}/${dashType}/${member}`;
}
