import com.github.gradle.node.npm.task.NpxTask

plugins {
    id("com.github.node-gradle.node") version "7.1.0"
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

val referenceDocsDeps = configurations.dependencyScope("referenceDocs").get()
val referenceDocs = configurations.resolvable("referenceDocsClasspath") {
    extendsFrom(referenceDocsDeps)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-reference-documentation"))
    }
}

configurations {
    consumable("gradleDocumentationSiteElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-documentation-site"))
        }
    }

    named("gradleDocumentationSiteElements") {
        outgoing.artifact(layout.buildDirectory.dir("site").get().asFile) {
            builtBy(buildDocs)
        }
    }
}

dependencies {
    add(gradleVersionFileDeps.name, project(":"))
    add(referenceDocsDeps.name, project(":reference-docs"))
}

val preparePublicDir = tasks.register<Sync>("preparePublicDir") {
    description = "Assembles Astro's publicDir under build/public from source assets (public/) and the rendered reference docs (javadoc, kotlin-dsl, dsl)."
    group = "documentation"
    from(layout.projectDirectory.dir("public"))
    from(referenceDocs)
    into(layout.buildDirectory.dir("public"))
}

val stageGradleVersion = tasks.register<Sync>("stageGradleVersion") {
    description = "Stages version.txt into the docs-site package so it can be imported by TS without reaching outside the package."
    from(gradleVersionFile)
    into(layout.buildDirectory.dir("generated"))
}

val cleanSiteOutput = tasks.register<Delete>("cleanSiteOutput") {
    description = "Removes the Astro build output directory before a fresh production build."
    delete(layout.buildDirectory.dir("site"))
}

val buildDocs = tasks.register<NpxTask>("buildDocs") {
    description = "Builds the complete documentation site (user guide + javadoc + kotlin-dsl + dsl)."
    group = "documentation"
    dependsOn(tasks.npmInstall, preparePublicDir, stageGradleVersion, cleanSiteOutput)
    command.set("astro")
    args.set(listOf("build"))
    inputs.file("package.json")
    inputs.file("package-lock.json")
    inputs.file("astro.config.ts")
    inputs.file("tsconfig.json")
    inputs.file("sidebar-structure.json")
    inputs.dir("src")
    inputs.dir(layout.buildDirectory.dir("public"))
    inputs.dir(layout.buildDirectory.dir("generated"))
    outputs.dir(layout.buildDirectory.dir("site"))
}

tasks.register<NpxTask>("serveDev") {
    description = """Starts a documentation development server.
        | File watching and hot-reloading enabled. Local search is unavailable in dev mode.
        | First startup builds the reference docs (javadoc, kotlin-dsl, dsl); subsequent runs are fast.
    """.trimMargin()
    group = "documentation"
    dependsOn(tasks.npmInstall, preparePublicDir, stageGradleVersion)
    command.set("astro")
    args.set(listOf("dev"))
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

tasks.register<Delete>("clean") {
    description = "Removes the Astro build output and Node tooling artifacts."
    group = "documentation"
    delete(layout.buildDirectory, "node_modules", ".astro")
}
