import com.github.gradle.node.npm.task.NpxTask

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

val cleanSiteOutput = tasks.register<Delete>("cleanSiteOutput") {
    description = "Removes the Astro build output directory before a fresh production build."
    delete(layout.buildDirectory.dir("site"))
}

val buildDocs = tasks.register<NpxTask>("buildDocs") {
    description = "Builds the complete documentation site (user guide + javadoc + kotlin-dsl + dsl)."
    group = "documentation"
    dependsOn(tasks.npmInstall, preparePublicDir, cleanSiteOutput)
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

tasks.register<NpxTask>("serveDev") {
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
    environment.put("ASTRO_OUT_DIR", "./build/site")
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
