// tag::mkdir-example[]
task("ensureDirectory") {
    doLast {
        mkdir("images")
    }
}
// end::mkdir-example[]

// tag::move-example[]
task("moveReports") {
    doLast {
        ant.withGroovyBuilder {
            "move"("file" to "${buildDir}/reports", "todir" to "${buildDir}/toArchive")
        }
    }
}
// end::move-example[]

// tag::delete-example[]
task<Delete>("myClean") {
    delete(buildDir)
}
// end::delete-example[]

// tag::delete-with-filter-example[]
task<Delete>("cleanTempFiles") {
    delete(fileTree("src").matching {
        include("**/*.tmp")
    })
}
// end::delete-with-filter-example[]
