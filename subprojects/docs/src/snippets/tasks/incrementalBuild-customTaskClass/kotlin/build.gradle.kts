import org.example.*

plugins {
    base
}

val processTemplates by tasks.registering(ProcessTemplates::class) {
    templateEngine.set(TemplateEngineType.FREEMARKER)
    sourceFiles.from(fileTree("src/templates"))
    templateData.name.set("test")
    templateData.variables.set(mapOf("year" to "2012"))
    outputDir.set(file(layout.buildDirectory.dir("genOutput")))
}

// tag::ad-hoc-task[]
tasks.register("processTemplatesAdHoc") {
    inputs.property("engine", TemplateEngineType.FREEMARKER)
    inputs.files(fileTree("src/templates"))
        .withPropertyName("sourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("templateData.name", "docs")
    inputs.property("templateData.variables", mapOf("year" to "2013"))
    outputs.dir(layout.buildDirectory.dir("genOutput2"))
        .withPropertyName("outputDir")

    doLast {
        // Process the templates here
// end::ad-hoc-task[]
        copy {
            into(layout.buildDirectory.dir("genOutput2"))
            from(fileTree("src/templates"))
            expand("year" to "2012")
        }

// tag::ad-hoc-task[]
    }
}
// end::ad-hoc-task[]

// tag::ad-hoc-task-skip-when-empty[]
tasks.register("processTemplatesAdHocSkipWhenEmpty") {
    // ...

    inputs.files(fileTree("src/templates") {
            include("**/*.fm")
        })
        .skipWhenEmpty()
        .withPropertyName("sourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .ignoreEmptyDirectories()

    // ...
// end::ad-hoc-task-skip-when-empty[]
    inputs.property("engine", TemplateEngineType.FREEMARKER)
    inputs.property("templateData.name", "docs")
    inputs.property("templateData.variables", mapOf("year" to "2013"))
    outputs.dir(layout.buildDirectory.dir("genOutput2"))
        .withPropertyName("outputDir")

    doLast {
        copy {
            into(layout.buildDirectory.dir("genOutput2"))
            from(fileTree("src/templates"))
            expand("year" to "2013")
        }
    }
// tag::ad-hoc-task-skip-when-empty[]
}
// end::ad-hoc-task-skip-when-empty[]

// tag::custom-class-runtime-api[]
tasks.register<ProcessTemplates>("processTemplatesWithExtraInputs") {
    // ...
// end::custom-class-runtime-api[]
    templateEngine.set(TemplateEngineType.FREEMARKER)
    sourceFiles.from(fileTree("src/templates"))
    templateData.name.set("test")
    templateData.variables.set(mapOf("year" to "2014"))
    outputDir.set(file(layout.buildDirectory.dir("genOutput3")))
// tag::custom-class-runtime-api[]

    inputs.file("src/headers/headers.txt")
        .withPropertyName("headers")
        .withPathSensitivity(PathSensitivity.NONE)
}
// end::custom-class-runtime-api[]

// tag::inferred-task-dep-via-outputs[]
tasks.register<Zip>("packageFiles") {
    from(processTemplates.map {it.outputs })
}
// end::inferred-task-dep-via-outputs[]

// tag::inferred-task-dep-via-task[]
tasks.register<Zip>("packageFiles2") {
    from(processTemplates)
}
// end::inferred-task-dep-via-task[]


// tag::adhoc-destroyable-task[]
tasks.register("removeTempDir") {
    destroyables.register(layout.projectDirectory.dir("tmpDir"))
    doLast {
        delete(layout.projectDirectory.dir("tmpDir"))
    }
}
// end::adhoc-destroyable-task[]

tasks.build {
    dependsOn(processTemplates, "processTemplatesAdHoc", "processTemplatesAdHocSkipWhenEmpty", "processTemplatesWithExtraInputs", "processTemplatesWithoutAnnotations")
}
