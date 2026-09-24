import { describe, expect, it } from "vitest";
import { extractTaggedRegion, selectSampleContent, stripTagMarkers } from "./sample-tags";

const BUILD_SCRIPT = `// tag::use-and-configure-plugin[]
plugins {
    \`java-gradle-plugin\`
// end::use-and-configure-plugin[]
    \`maven-publish\`
// tag::use-and-configure-plugin[]
}
// end::use-and-configure-plugin[]

group = "org.example"
version = "1.0-SNAPSHOT"

// tag::use-and-configure-plugin[]
gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "org.example.greeting"
            implementationClass = "org.example.GreetingPlugin"
        }
    }
}
// end::use-and-configure-plugin[]

publishing {
    // tag::local-maven[]
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
    // end::local-maven[]
}`;

describe("extractTaggedRegion", () => {
  it("concatenates multiple regions with the same tag and skips interleaved content", () => {
    expect(extractTaggedRegion(BUILD_SCRIPT, "use-and-configure-plugin")).toBe(
      `plugins {
    \`java-gradle-plugin\`
}
gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "org.example.greeting"
            implementationClass = "org.example.GreetingPlugin"
        }
    }
}`,
    );
  });

  it("extracts an independent tag untouched by others", () => {
    expect(extractTaggedRegion(BUILD_SCRIPT, "local-maven")).toBe(
      `    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }`,
    );
  });

  it("supports multiple tags separated by ; or ,", () => {
    const result = extractTaggedRegion(BUILD_SCRIPT, "use-and-configure-plugin;local-maven");
    expect(result).toContain("gradlePlugin {");
    expect(result).toContain('url = uri(layout.buildDirectory.dir("repo"))');
    expect(result).not.toContain("maven-publish");
  });

  it("throws when the tag matches nothing", () => {
    expect(() => extractTaggedRegion(BUILD_SCRIPT, "no-such-tag", "build.gradle.kts")).toThrow(
      /no-such-tag.*build\.gradle\.kts/s,
    );
  });
});

describe("stripTagMarkers", () => {
  it("removes every tag directive line but keeps all code", () => {
    const stripped = stripTagMarkers(BUILD_SCRIPT);
    expect(stripped).not.toMatch(/tag::|end::/);
    expect(stripped).toContain("`maven-publish`");
    expect(stripped).toContain("publishing {");
  });

  it("handles xml comment markers", () => {
    const xml = `<beans>
<!-- tag::bean[] -->
<bean id="a"/>
<!-- end::bean[] -->
</beans>`;
    expect(stripTagMarkers(xml)).toBe(`<beans>
<bean id="a"/>
</beans>`);
    expect(extractTaggedRegion(xml, "bean")).toBe('<bean id="a"/>');
  });
});

describe("selectSampleContent", () => {
  it("extracts when a tag is given, strips markers otherwise", () => {
    expect(selectSampleContent(BUILD_SCRIPT, "local-maven", "f")).toContain("repositories {");
    expect(selectSampleContent(BUILD_SCRIPT, undefined, "f")).toContain("`maven-publish`");
    expect(selectSampleContent(BUILD_SCRIPT, undefined, "f")).not.toContain("tag::");
  });

  it("falls back to the whole file (stripped) when the tag matches nothing", () => {
    const result = selectSampleContent(BUILD_SCRIPT, "no-such-tag", "f");
    expect(result).toContain("`maven-publish`");
    expect(result).toContain("publishing {");
    expect(result).not.toContain("tag::");
  });
});
