import org.example.*

plugins {
    base
}

val processTemplates by tasks.registering(ProcessTemplates::class) {
    templateEngine = TemplateEngineType.FREEMARKER
    sourceFiles = fileTree("src/templates")
    templateData = TemplateData("test", mapOf("year" to "2012"))
    outputDir = file("$buildDir/genOutput")
}

// tag::ad-hoc-task[]
tasks.register("processTemplatesAdHoc") {
    inputs.property("engine", TemplateEngineType.FREEMARKER)
    inputs.files(fileTree("src/templates"))
        .withPropertyName("sourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("templateData.name", "docs")
    inputs.property("templateData.variables", mapOf("year" to "2013"))
    outputs.dir("$buildDir/genOutput2")
        .withPropertyName("outputDir")

    doLast {
        // Process the templates here
// end::ad-hoc-task[]
        copy {
            into("$buildDir/genOutput2")
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

    inputs.files(fileTree("src/templates"))
        .skipWhenEmpty()
        .withPropertyName("sourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // ...
// end::ad-hoc-task-skip-when-empty[]
    inputs.property("engine", TemplateEngineType.FREEMARKER)
    inputs.property("templateData.name", "docs")
    inputs.property("templateData.variables", mapOf("year" to "2013"))
    outputs.dir("$buildDir/genOutput2")
        .withPropertyName("outputDir")

    doLast {
        // Process the templates here
        copy {
            into("$buildDir/genOutput2")
            from(fileTree("src/templates"))
            expand("year" to "2012")
        }
    }
// tag::ad-hoc-task-skip-when-empty[]
}
// end::ad-hoc-task-skip-when-empty[]

// tag::custom-class-runtime-api[]
tasks.register<ProcessTemplates>("processTemplatesWithExtraInputs") {
    // ...
// end::custom-class-runtime-api[]
    templateEngine = TemplateEngineType.FREEMARKER
    sourceFiles = fileTree("src/templates")
    templateData = TemplateData("test", mapOf("year" to "2014"))
    outputDir = file("$buildDir/genOutput3")
// tag::custom-class-runtime-api[]

    inputs.file("src/headers/headers.txt")
        .withPropertyName("headers")
        .withPathSensitivity(PathSensitivity.NONE)
}
// end::custom-class-runtime-api[]

tasks.register<ProcessTemplatesNoAnnotations>("processTemplatesWithoutAnnotations") {
    templateEngine = TemplateEngineType.FREEMARKER
    sourceFiles = fileTree("src/templates")
    templateData = TemplateData("test", mapOf("year" to "2014"))
    outputDir = file("$buildDir/genOutput3")
}

// tag::inferred-task-dep-via-outputs[]
tasks.register<Zip>("packageFiles") {
    from(processTemplates.get().outputs)
}
// end::inferred-task-dep-via-outputs[]

// tag::inferred-task-dep-via-task[]
tasks.register<Zip>("packageFiles2") {
    from(processTemplates)
}
// end::inferred-task-dep-via-task[]


// tag::adhoc-destroyable-task[]
tasks.register("removeTempDir") {
    destroyables.register("$projectDir/tmpDir")
    doLast {
        delete("$projectDir/tmpDir")
    }
}
// end::adhoc-destroyable-task[]

tasks.build { dependsOn(processTemplates, "processTemplatesAdHoc", "processTemplatesRuntime", "processTemplatesRuntimeConf") }
