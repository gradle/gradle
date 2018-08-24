// tag::mkdir-example[]
tasks.create("ensureDirectory") {
    doLast {
        mkdir("images")
    }
}
// end::mkdir-example[]

// tag::move-example[]
tasks.create("moveReports") {
    doLast {
        ant.withGroovyBuilder {
            "move"(mapOf("file" to "${buildDir}/reports", "todir" to "${buildDir}/toArchive"))
        }
    }
}
// end::move-example[]

// tag::delete-example[]
tasks.create<Delete>("myClean") {
    delete(buildDir)
}
// end::delete-example[]

// tag::delete-with-filter-example[]
tasks.create<Delete>("cleanTempFiles") {
    delete(fileTree("src").matching {
        include("**/*.tmp")
    })
}
// end::delete-with-filter-example[]
