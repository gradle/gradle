import com.github.gradle.node.npm.task.NpxTask
import com.github.gradle.node.task.NodeTask

plugins {
    id("com.github.node-gradle.node") version "7.1.0"
    id("gradlebuild.docs-site")
    base
}

node {
    version.set("24.16.0")
    download.set(true)
    npmInstallCommand.set("ci")
}

val gradleVersionFileDeps = configurations.dependencyScope("gradleVersionFile").get()
val gradleVersionFile = configurations.resolvable("gradleVersionFileClasspath") {
    extendsFrom(gradleVersionFileDeps)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("metadata"))
    }
}

val referenceDocs = configurations.named("referenceDocsClasspath")

dependencies {
    add(gradleVersionFileDeps.name, project(":"))
}

val preparePublicDir = tasks.register<Sync>("preparePublicDir") {
    description = "Assembles Astro's publicDir under build/public from source assets (public/) and the rendered reference docs (javadoc, kotlin-dsl, dsl)."
    group = "documentation"
    from(layout.projectDirectory.dir("public"))
    from(referenceDocs)
    into(layout.buildDirectory.dir("public"))
}

// src/config/variables.ts picks this up and substitutes it into doc content at build time.
val gradleVersion = gradleVersionFile.flatMap { it.elements }.map { it.single().asFile.readText().trim() }

val buildDocs = tasks.register<NpxTask>("buildDocs") {
    description = "Builds the complete documentation site (user guide + javadoc + kotlin-dsl + dsl)."
    group = "documentation"
    dependsOn(tasks.npmInstall, preparePublicDir)
    command.set("astro")
    args.set(listOf("build"))
    environment.put("PUBLIC_GRADLE_VERSION", gradleVersion)
    environment.put("ASTRO_PUBLIC_DIR", "./build/public")
    environment.put("ASTRO_OUT_DIR", "./build/site")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    inputs.file("astro.config.ts")
    inputs.file("ec.config.mjs")
    inputs.file("tsconfig.json")
    inputs.file("sidebar-structure.json")
    inputs.file("sidebar-structure.ts")
    inputs.dir("plugins")
    inputs.dir("src")
    inputs.dir(layout.buildDirectory.dir("public"))
    outputs.dir(layout.buildDirectory.dir("site"))
    // Start each production build from an empty output directory so a page that
    // stops being generated cannot survive in the published site. Held in a
    // local and run in doFirst rather than as a separate Delete task: a Delete
    // always executes, which would leave this task permanently out of date.
    val siteDir = layout.buildDirectory.dir("site")
    doFirst {
        siteDir.get().asFile.deleteRecursively()
    }
}

configurations.named("gradleDocumentationSiteElements") {
    outgoing.artifact(layout.buildDirectory.dir("site").get().asFile) {
        builtBy(buildDocs)
    }
}

// Wire the site build into the standard lifecycle so `assemble` (and thus `build`) produces the site.
tasks.named("assemble") {
    dependsOn(buildDocs)
}

tasks.register<NpxTask>("serveDocs") {
    description = """Starts a documentation development server.
        | File watching and hot-reloading enabled. Local search is unavailable in dev mode.
        | First startup builds the reference docs (javadoc, kotlin-dsl, dsl); subsequent runs are fast.
    """.trimMargin()
    group = "documentation"
    dependsOn(tasks.npmInstall, preparePublicDir)
    command.set("astro")
    args.set(listOf("dev"))
    environment.put("PUBLIC_GRADLE_VERSION", gradleVersion)
    environment.put("ASTRO_PUBLIC_DIR", "./build/public")
}

tasks.register<NpxTask>("serveProd") {
    description = """Serves the production build of the documentation
        | No file watching or hot-reloading.
        | Local search is available in production mode.
    """.trimMargin()
    group = "documentation"
    dependsOn("buildDocs")
    command.set("astro")
    args.set(listOf("preview"))
    // `astro preview` re-reads astro.config.ts, which derives the site's base
    // path from this version. Without it the config falls back to its stub and
    // preview serves under a prefix the built tree was never written for, so
    // every asset 404s.
    environment.put("PUBLIC_GRADLE_VERSION", gradleVersion)
    environment.put("ASTRO_OUT_DIR", "./build/site")
}

// The Astro build enforces most of the acceptance gates itself: the Markdown
// emitter (src/pages/[...slug]/index.md.ts) fails the build on a component with
// no registered renderer, on JSX surviving into an emitted .md, on a relative
// link, and on a page over the size limit. `checkDocs` adds the source-level
// checks that run before rendering and gives CI one task to call.
tasks.register<NodeTask>("checkDocs") {
    description = """Verifies the documentation sources and the generated site.
        | Compiles every page with the real MDX compiler, checks each callout list against the
        | markers in its preceding code block, and (via buildDocs) enforces the Markdown-emitter
        | gates: every component has a Markdown rendering, no JSX survives into a .md, every link
        | is absolute, and no page exceeds the size budget.
    """.trimMargin()
    group = "verification"
    dependsOn(tasks.npmInstall, "buildDocs")
    script.set(layout.projectDirectory.file("tools/check-content.mjs"))
    inputs.dir("src/content")
    inputs.file("tools/check-content.mjs")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    // Held in a local so the doLast action captures the provider rather than a
    // reference to the build script, which the configuration cache cannot
    // serialize. The marker exists only to give the task an output, so Gradle
    // can skip it when nothing it reads has changed.
    val marker = layout.buildDirectory.file("reports/check-docs.ok")
    outputs.file(marker)
    doLast {
        val file = marker.get().asFile
        file.parentFile.mkdirs()
        file.writeText("ok\n")
    }
}

// Make the standard lifecycle run it, so CI picks it up without extra wiring.
tasks.named("check") {
    dependsOn("checkDocs")
}

tasks.register<NpxTask>("formatCheck") {
    description = "Verifies if all files are formatted according to Prettier's rules."
    group = "verification"
    dependsOn(tasks.npmInstall)
    command.set("prettier")
    args.set(listOf("--check", "src/**/*.mdx"))
    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("package-lock.json")
}

tasks.register<NpxTask>("formatWrite") {
    description = "Reformats files according to Prettier's rules."
    group = "documentation"
    dependsOn(tasks.npmInstall)
    command.set("prettier")
    args.set(listOf("--write", "src/**/*.mdx"))
}

// `base` already registers `clean` to remove the build directory; extend it to also drop the Node tooling artifacts.
tasks.named<Delete>("clean") {
    delete("node_modules", ".astro")
}
