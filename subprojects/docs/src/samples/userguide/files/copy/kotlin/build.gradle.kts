// tag::filter-files[]
import org.apache.tools.ant.filters.FixCrLfFilter
import org.apache.tools.ant.filters.ReplaceTokens
// end::filter-files[]
import java.util.concurrent.Callable

version = "1.1"

// tag::copy-single-file-example[]
task<Copy>("copyReport") {
    from(file("$buildDir/reports/my-report.pdf"))
    into(file("$buildDir/toArchive"))
}
// end::copy-single-file-example[]

// tag::copy-single-file-example-without-file-method[]
task<Copy>("copyReport2") {
    from("$buildDir/reports/my-report.pdf")
    into("$buildDir/toArchive")
}
// end::copy-single-file-example-without-file-method[]

val myReportTask by tasks.creating {
    val outputFile by extra { file("$buildDir/reports/my-report.pdf") }

    outputs.files(outputFile)
    doLast {
        outputFile.setLastModified(System.currentTimeMillis())
    }
}

val archiveReportsTask by tasks.creating {
    val dirToArchive by extra { file("$buildDir/toArchive") }
    inputs.dir(dirToArchive)
}

// tag::copy-single-file-example-with-task-properties[]
task<Copy>("copyReport3") {
    val outputFile: File by myReportTask.extra
    val dirToArchive: File by archiveReportsTask.extra
    from(outputFile)
    into(dirToArchive)
}
// end::copy-single-file-example-with-task-properties[]

// tag::copy-multiple-files-example[]
task<Copy>("copyReportsForArchiving") {
    from("$buildDir/reports/my-report.pdf", "src/docs/manual.pdf")
    into("$buildDir/toArchive")
}
// end::copy-multiple-files-example[]

// tag::copy-multiple-files-with-flat-filter-example[]
task<Copy>("copyPdfReportsForArchiving") {
    from("$buildDir/reports")
    include("*.pdf")
    into("$buildDir/toArchive")
}
// end::copy-multiple-files-with-flat-filter-example[]

// tag::copy-multiple-files-with-deep-filter-example[]
task<Copy>("copyAllPdfReportsForArchiving") {
    from("$buildDir/reports")
    include("**/*.pdf")
    into("$buildDir/toArchive")
}
// end::copy-multiple-files-with-deep-filter-example[]


// tag::copy-directory-example[]
task<Copy>("copyReportsDirForArchiving") {
    from("$buildDir/reports")
    into("$buildDir/toArchive")
}
// end::copy-directory-example[]

// tag::copy-directory-including-itself-example[]
task<Copy>("copyReportsDirForArchiving2") {
    from("$buildDir") {
        include("reports/**")
    }
    into("$buildDir/toArchive")
}
// end::copy-directory-including-itself-example[]

// tag::create-archive-example[]
task<Zip>("packageDistribution") {
    archiveName = "my-distribution.zip"
    destinationDir = file("$buildDir/dist")

    from("$buildDir/toArchive")
}
// end::create-archive-example[]

// tag::rename-on-copy-example[]
task<Copy>("copyFromStaging") {
    from("src/main/webapp")
    into("$buildDir/explodedWar")

    rename("(.+)-staging(.+)", "$1$2")
}
// end::rename-on-copy-example[]

// tag::truncate-names-example[]
task<Copy>("copyWithTruncate") {
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







val copyTask by tasks.creating(Copy::class) {
    from("src/main/webapp")
    into("$buildDir/explodedWar")
}

// tag::copy-task-with-patterns[]
task<Copy>("copyTaskWithPatterns") {
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
task<Copy>("anotherCopyTask") {
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
    into(Callable { getDestDir() })
}
// end::copy-task-2[]

fun getDestDir() = file("some-dir")

// tag::copy-method[]
task("copyMethod") {
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
task("copyMethodWithExplicitDependencies") {
    // up-to-date check for inputs, plus add copyTask as dependency
    inputs.files(copyTask)
    outputs.dir("some-dir") // up-to-date check for outputs
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
task<Copy>("rename") {
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
task<Copy>("filter") {
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

task("test") {
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

task<Copy>("copyAssets") {
    into("$buildDir/inPlaceApp")
    with(webAssetsSpec)
}

task<Zip>("distApp") {
    archiveName = "my-app-dist.zip"
    destinationDir = file("$buildDir/dists")

    from(appClasses)
    with(webAssetsSpec)
}
// end::standalone-copyspec[]

// tag::shared-copy-patterns[]
val webAssetPatterns = Action<CopySpec> {
    include("**/*.html", "**/*.png", "**/*.jpg")
}

task<Copy>("copyAppAssets") {
    into("$buildDir/inPlaceApp")
    from("src/main/webapp", webAssetPatterns)
}

task<Zip>("archiveDistAssets") {
    archiveName = "distribution-assets.zip"
    destinationDir = file("$buildDir/dists")

    from("distResources", webAssetPatterns)
}
// end::shared-copy-patterns[]

// tag::change-default-exclusions[]
task<Copy>("forcedCopy") {
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
