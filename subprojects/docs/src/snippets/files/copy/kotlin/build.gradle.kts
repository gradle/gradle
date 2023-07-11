// tag::filter-files[]
import org.apache.tools.ant.filters.FixCrLfFilter
import org.apache.tools.ant.filters.ReplaceTokens
// end::filter-files[]

version = "1.1"

// tag::copy-single-file-example[]
tasks.register<Copy>("copyReport") {
    from(layout.buildDirectory.file("reports/my-report.pdf"))
    into(layout.buildDirectory.dir("toArchive"))
}
// end::copy-single-file-example[]

// tag::copy-single-file-example-without-file-method[]
tasks.register<Copy>("copyReport2") {
    from("$buildDir/reports/my-report.pdf")
    into("$buildDir/toArchive")
}
// end::copy-single-file-example-without-file-method[]

abstract class MyReportTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun createFile() {
        outputFile.get().asFile.writeText("Report contents")
    }
}

val myReportTask by tasks.registering(MyReportTask::class) {
    outputFile = file("$buildDir/reports/my-report.pdf")
}

abstract class MyArchiveTask : DefaultTask() {
    @get:InputDirectory
    abstract val dirToArchive: DirectoryProperty
}

val archiveReportsTask by tasks.registering(MyArchiveTask::class) {
    dirToArchive = file("$buildDir/toArchive")
}

// tag::copy-single-file-example-with-task-properties[]
tasks.register<Copy>("copyReport3") {
    from(myReportTask.get().outputFile)
    into(archiveReportsTask.get().dirToArchive)
}
// end::copy-single-file-example-with-task-properties[]

// tag::copy-multiple-files-example[]
tasks.register<Copy>("copyReportsForArchiving") {
    from(layout.buildDirectory.file("reports/my-report.pdf"), layout.projectDirectory.file("src/docs/manual.pdf"))
    into(layout.buildDirectory.dir("toArchive"))
}
// end::copy-multiple-files-example[]

// tag::copy-multiple-files-with-flat-filter-example[]
tasks.register<Copy>("copyPdfReportsForArchiving") {
    from(layout.buildDirectory.dir("reports"))
    include("*.pdf")
    into(layout.buildDirectory.dir("toArchive"))
}
// end::copy-multiple-files-with-flat-filter-example[]

// tag::copy-multiple-files-with-deep-filter-example[]
tasks.register<Copy>("copyAllPdfReportsForArchiving") {
    from(layout.buildDirectory.dir("reports"))
    include("**/*.pdf")
    into(layout.buildDirectory.dir("toArchive"))
}
// end::copy-multiple-files-with-deep-filter-example[]


// tag::copy-directory-example[]
tasks.register<Copy>("copyReportsDirForArchiving") {
    from(layout.buildDirectory.dir("reports"))
    into(layout.buildDirectory.dir("toArchive"))
}
// end::copy-directory-example[]

// tag::copy-directory-including-itself-example[]
tasks.register<Copy>("copyReportsDirForArchiving2") {
    from(layout.buildDirectory) {
        include("reports/**")
    }
    into(layout.buildDirectory.dir("toArchive"))
}
// end::copy-directory-including-itself-example[]

// tag::create-archive-example[]
tasks.register<Zip>("packageDistribution") {
    archiveFileName = "my-distribution.zip"
    destinationDirectory = layout.buildDirectory.dir("dist")

    from(layout.buildDirectory.dir("toArchive"))
}
// end::create-archive-example[]

// tag::rename-on-copy-example[]
tasks.register<Copy>("copyFromStaging") {
    from("src/main/webapp")
    into(layout.buildDirectory.dir("explodedWar"))

    rename("(.+)-staging(.+)", "$1$2")
}
// end::rename-on-copy-example[]

// tag::truncate-names-example[]
tasks.register<Copy>("copyWithTruncate") {
    from(layout.buildDirectory.dir("reports"))
    rename { filename: String ->
        if (filename.length > 10) {
            filename.slice(0..7) + "~" + filename.length
        }
        else filename
    }
    into(layout.buildDirectory.dir("toArchive"))
}
// end::truncate-names-example[]







val copyTask by tasks.registering(Copy::class) {
    from("src/main/webapp")
    into(layout.buildDirectory.dir("explodedWar"))
}

// tag::copy-task-with-patterns[]
tasks.register<Copy>("copyTaskWithPatterns") {
    from("src/main/webapp")
    into(layout.buildDirectory.dir("explodedWar"))
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

tasks.named<Copy>("anotherCopyTask") {
    // The task uses many sources that produce overlapping outputs.
    // This isn't a part of any snippet, but is necessary to make the build work.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

fun getDestDir() = file("some-dir")

// tag::copy-method[]
tasks.register("copyMethod") {
    doLast {
        copy {
            from("src/main/webapp")
            into(layout.buildDirectory.dir("explodedWar"))
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
    into(layout.buildDirectory.dir("explodedWar"))
    // Use a regular expression to map the file name
    rename("(.+)-staging(.+)", "$1$2")
    rename("(.+)-staging(.+)".toRegex().pattern, "$1$2")
    // Use a closure to convert all file names to upper case
    rename { fileName: String ->
        fileName.toUpperCase()
    }
}
// end::rename-files[]

// tag::filter-files[]
tasks.register<Copy>("filter") {
    from("src/main/webapp")
    into(layout.buildDirectory.dir("explodedWar"))
    // Substitute property tokens in files
    expand("copyright" to "2009", "version" to "2.3.1")
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

// tag::file-permissions[]
tasks.register<Copy>("permissions") {
    from("src/main/webapp")
    into(layout.buildDirectory.dir("explodedWar"))
    filePermissions {
        user {
            read = true
            execute = true
        }
        other.execute = false
    }
    dirPermissions {
        unix("r-xr-x---")
    }
}
// end::file-permissions[]

tasks.register("test") {
    dependsOn(tasks.withType<Copy>())
    dependsOn(tasks["copyMethod"])
    dependsOn(tasks["copyMethodWithExplicitDependencies"])
}

val appClasses = layout.buildDirectory.dir("classes")

// tag::standalone-copyspec[]
val webAssetsSpec: CopySpec = copySpec {
    from("src/main/webapp")
    include("**/*.html", "**/*.png", "**/*.jpg")
    rename("(.+)-staging(.+)", "$1$2")
}

tasks.register<Copy>("copyAssets") {
    into(layout.buildDirectory.dir("inPlaceApp"))
    with(webAssetsSpec)
}

tasks.register<Zip>("distApp") {
    archiveFileName = "my-app-dist.zip"
    destinationDirectory = layout.buildDirectory.dir("dists")

    from(appClasses)
    with(webAssetsSpec)
}
// end::standalone-copyspec[]

// tag::shared-copy-patterns[]
val webAssetPatterns = Action<CopySpec> {
    include("**/*.html", "**/*.png", "**/*.jpg")
}

tasks.register<Copy>("copyAppAssets") {
    into(layout.buildDirectory.dir("inPlaceApp"))
    from("src/main/webapp", webAssetPatterns)
}

tasks.register<Zip>("archiveDistAssets") {
    archiveFileName = "distribution-assets.zip"
    destinationDirectory = layout.buildDirectory.dir("dists")

    from("distResources", webAssetPatterns)
}
// end::shared-copy-patterns[]
