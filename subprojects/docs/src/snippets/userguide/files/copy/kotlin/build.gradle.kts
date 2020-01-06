// tag::filter-files[]
import org.apache.tools.ant.filters.FixCrLfFilter
import org.apache.tools.ant.filters.ReplaceTokens
// end::filter-files[]

version = "1.1"

// tag::copy-single-file-example[]
tasks.register<Copy>("copyReport") {
    from(file("$buildDir/reports/my-report.pdf"))
    into(file("$buildDir/toArchive"))
}
// end::copy-single-file-example[]

// tag::copy-single-file-example-without-file-method[]
tasks.register<Copy>("copyReport2") {
    from("$buildDir/reports/my-report.pdf")
    into("$buildDir/toArchive")
}
// end::copy-single-file-example-without-file-method[]

val myReportTask by tasks.registering {
    val outputFile by extra { file("$buildDir/reports/my-report.pdf") }

    outputs.files(outputFile)
    doLast {
        outputFile.setLastModified(System.currentTimeMillis())
    }
}

val archiveReportsTask by tasks.registering {
    val dirToArchive by extra { file("$buildDir/toArchive") }
    inputs.dir(dirToArchive)
}

// tag::copy-single-file-example-with-task-properties[]
tasks.register<Copy>("copyReport3") {
    val outputFile: File by myReportTask.get().extra
    val dirToArchive: File by archiveReportsTask.get().extra
    from(outputFile)
    into(dirToArchive)
}
// end::copy-single-file-example-with-task-properties[]

// tag::copy-multiple-files-example[]
tasks.register<Copy>("copyReportsForArchiving") {
    from("$buildDir/reports/my-report.pdf", "src/docs/manual.pdf")
    into("$buildDir/toArchive")
}
// end::copy-multiple-files-example[]

// tag::copy-multiple-files-with-flat-filter-example[]
tasks.register<Copy>("copyPdfReportsForArchiving") {
    from("$buildDir/reports")
    include("*.pdf")
    into("$buildDir/toArchive")
}
// end::copy-multiple-files-with-flat-filter-example[]

// tag::copy-multiple-files-with-deep-filter-example[]
tasks.register<Copy>("copyAllPdfReportsForArchiving") {
    from("$buildDir/reports")
    include("**/*.pdf")
    into("$buildDir/toArchive")
}
// end::copy-multiple-files-with-deep-filter-example[]


// tag::copy-directory-example[]
tasks.register<Copy>("copyReportsDirForArchiving") {
    from("$buildDir/reports")
    into("$buildDir/toArchive")
}
// end::copy-directory-example[]

// tag::copy-directory-including-itself-example[]
tasks.register<Copy>("copyReportsDirForArchiving2") {
    from("$buildDir") {
        include("reports/**")
    }
    into("$buildDir/toArchive")
}
// end::copy-directory-including-itself-example[]

// tag::create-archive-example[]
tasks.register<Zip>("packageDistribution") {
    archiveFileName.set("my-distribution.zip")
    destinationDirectory.set(file("$buildDir/dist"))

    from("$buildDir/toArchive")
}
// end::create-archive-example[]

// tag::rename-on-copy-example[]
tasks.register<Copy>("copyFromStaging") {
    from("src/main/webapp")
    into("$buildDir/explodedWar")

    rename("(.+)-staging(.+)", "$1$2")
}
// end::rename-on-copy-example[]

// tag::truncate-names-example[]
tasks.register<Copy>("copyWithTruncate") {
    from("$buildDir/reports")
    rename { filename: String ->
        if (filename.length > 10) {
            filename.slice(0..7) + "~" + filename.length
        }
        else filename
    }
    into("$buildDir/toArchive")
}
// end::truncate-names-example[]







val copyTask by tasks.registering(Copy::class) {
    from("src/main/webapp")
    into("$buildDir/explodedWar")
}

// tag::copy-task-with-patterns[]
tasks.register<Copy>("copyTaskWithPatterns") {
    from("src/main/webapp")
    into("$buildDir/explodedWar")
    include("**/*.html")
    include("**/*.jsp")
    exclude { details: FileTreeElement ->
        details.file.name.endsWith(".html") &&
            details.file.readText().contains("DRAFT")
    }
}
// end::copy-task-with-patterns[]

// tag::copy-task-2[]
tasks.register<Copy>("anotherCopyTask") {
    // Copy everything under src/main/webapp
    from("src/main/webapp")
    // Copy a single file
    from("src/staging/index.html")
    // Copy the output of a task
    from(copyTask)
    // Copy the output of a task using Task outputs explicitly.
    from(tasks["copyTaskWithPatterns"].outputs)
    // Copy the contents of a Zip file
    from(zipTree("src/main/assets.zip"))
    // Determine the destination directory later
    into({ getDestDir() })
}
// end::copy-task-2[]

fun getDestDir() = file("some-dir")

// tag::copy-method[]
tasks.register("copyMethod") {
    doLast {
        copy {
            from("src/main/webapp")
            into("$buildDir/explodedWar")
            include("**/*.html")
            include("**/*.jsp")
        }
    }
}
// end::copy-method[]

// tag::copy-method-with-dependency[]
tasks.register("copyMethodWithExplicitDependencies") {
    // up-to-date check for inputs, plus add copyTask as dependency
    inputs.files(copyTask)
        .withPropertyName("inputs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir("some-dir") // up-to-date check for outputs
        .withPropertyName("outputDir")
    doLast {
        copy {
            // Copy the output of copyTask
            from(copyTask)
            into("some-dir")
        }
    }
}
// end::copy-method-with-dependency[]

configurations { "runtime" }

// tag::rename-files[]
tasks.register<Copy>("rename") {
    from("src/main/webapp")
    into("$buildDir/explodedWar")
    // Use a closure to convert all file names to upper case
    rename { fileName: String ->
        fileName.toUpperCase()
    }
    // Use a regular expression to map the file name
    rename("(.+)-staging-(.+)", "$1$2")
    rename("(.+)-staging-(.+)".toRegex().pattern, "$1$2")
}
// end::rename-files[]

// tag::filter-files[]
tasks.register<Copy>("filter") {
    from("src/main/webapp")
    into("$buildDir/explodedWar")
    // Substitute property tokens in files
    expand("copyright" to "2009", "version" to "2.3.1")
    expand(project.properties)
    // Use some of the filters provided by Ant
    filter(FixCrLfFilter::class)
    filter(ReplaceTokens::class, "tokens" to mapOf("copyright" to "2009", "version" to "2.3.1"))
    // Use a closure to filter each line
    filter { line: String ->
        "[$line]"
    }
    // Use a closure to remove lines
    filter { line: String ->
        if (line.startsWith('-')) null else line
    }
    filteringCharset = "UTF-8"
}
// end::filter-files[]

tasks.register("test") {
    dependsOn(tasks.withType<Copy>())
    dependsOn(tasks["copyMethod"])
    dependsOn(tasks["copyMethodWithExplicitDependencies"])
}

val appClasses = layout.files("$buildDir/classes")

// tag::standalone-copyspec[]
val webAssetsSpec: CopySpec = copySpec {
    from("src/main/webapp")
    include("**/*.html", "**/*.png", "**/*.jpg")
    rename("(.+)-staging(.+)", "$1$2")
}

tasks.register<Copy>("copyAssets") {
    into("$buildDir/inPlaceApp")
    with(webAssetsSpec)
}

tasks.register<Zip>("distApp") {
    archiveFileName.set("my-app-dist.zip")
    destinationDirectory.set(file("$buildDir/dists"))

    from(appClasses)
    with(webAssetsSpec)
}
// end::standalone-copyspec[]

// tag::shared-copy-patterns[]
val webAssetPatterns = Action<CopySpec> {
    include("**/*.html", "**/*.png", "**/*.jpg")
}

tasks.register<Copy>("copyAppAssets") {
    into("$buildDir/inPlaceApp")
    from("src/main/webapp", webAssetPatterns)
}

tasks.register<Zip>("archiveDistAssets") {
    archiveFileName.set("distribution-assets.zip")
    destinationDirectory.set(file("$buildDir/dists"))

    from("distResources", webAssetPatterns)
}
// end::shared-copy-patterns[]

// tag::change-default-exclusions[]
tasks.register<Copy>("forcedCopy") {
    into("$buildDir/inPlaceApp")
    from("src/main/webapp")

    doFirst {
        ant.withGroovyBuilder {
            "defaultexcludes"("remove" to "**/.git")
            "defaultexcludes"("remove" to "**/.git/**")
            "defaultexcludes"("remove" to "**/*~")
        }
    }

    doLast {
        ant.withGroovyBuilder {
            "defaultexcludes"("default" to true)
        }
    }
}
// end::change-default-exclusions[]
