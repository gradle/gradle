import { describe, expect, it } from "vitest";
import { groovyDslUrl, javadocUrl, kotlinDslUrl } from "./api-doc-url";

describe("javadocUrl", () => {
  it("maps a class to its package path", () => {
    expect(javadocUrl("org.gradle.api.Project")).toBe("/javadoc/org/gradle/api/Project.html");
  });

  it("appends the method anchor verbatim", () => {
    expect(javadocUrl("org.gradle.api.Project", "getName()")).toBe(
      "/javadoc/org/gradle/api/Project.html#getName()",
    );
  });

  it("keeps nested classes dot-joined in the filename", () => {
    expect(javadocUrl("org.gradle.api.Task.Namer")).toBe("/javadoc/org/gradle/api/Task.Namer.html");
  });
});

describe("groovyDslUrl", () => {
  it("uses the dotted class name as the filename", () => {
    expect(groovyDslUrl("org.gradle.api.Project")).toBe("/dsl/org.gradle.api.Project.html");
  });

  it("re-qualifies the method anchor with the class name", () => {
    expect(groovyDslUrl("org.gradle.api.Project", "allprojects(groovy.lang.Closure)")).toBe(
      "/dsl/org.gradle.api.Project.html#org.gradle.api.Project:allprojects(groovy.lang.Closure)",
    );
  });
});

describe("kotlinDslUrl", () => {
  it("dash-cases the type into Dokka's layout", () => {
    expect(kotlinDslUrl("org.gradle.api.DefaultTask")).toBe(
      "/kotlin-dsl/gradle/org.gradle.api/-default-task/index.html",
    );
  });

  it("uses the member as the page name", () => {
    expect(kotlinDslUrl("org.gradle.api.Project", "repositories")).toBe(
      "/kotlin-dsl/gradle/org.gradle.api/-project/repositories.html",
    );
  });
});
