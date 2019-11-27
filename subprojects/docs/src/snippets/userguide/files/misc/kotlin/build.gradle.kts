// tag::mkdir-example[]
tasks.register("ensureDirectory") {
    doLast {
        mkdir("images")
    }
}
// end::mkdir-example[]

// tag::move-example[]
tasks.register("moveReports") {
    doLast {
        ant.withGroovyBuilder {
            "move"("file" to "${buildDir}/reports", "todir" to "${buildDir}/toArchive")
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
