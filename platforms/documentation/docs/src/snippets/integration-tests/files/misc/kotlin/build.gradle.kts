import java.nio.file.Files

// tag::mkdir-example[]
tasks.register("ensureDirectory") {
    // Store target directory into a variable to avoid project reference in the configuration cache
    val directory = file("images")

    doLast {
        Files.createDirectories(directory.toPath())
    }
}
// end::mkdir-example[]

// tag::move-example[]
tasks.register("moveReports") {
    // Store the build directory into a variable to avoid project reference in the configuration cache
    val dir = buildDir

    doLast {
        ant.withGroovyBuilder {
            "move"("file" to "${dir}/reports", "todir" to "${dir}/toArchive")
        }
    }
}
// end::move-example[]

// tag::delete-example[]
tasks.register<Delete>("myClean") {
    delete(buildDir)
}
// end::delete-example[]

// tag::delete-with-filter-example[]
tasks.register<Delete>("cleanTempFiles") {
    delete(fileTree("src").matching {
        include("**/*.tmp")
    })
}
// end::delete-with-filter-example[]
