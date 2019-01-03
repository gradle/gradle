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
    inputs.property("templateData.name", "docs")
    inputs.property("templateData.variables", mapOf("year" to "2013"))
    outputs.dir("$buildDir/genOutput2")

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

// tag::custom-class-runtime-api[]
tasks.register<ProcessTemplatesNoAnnotations>("processTemplatesRuntime") {
    templateEngine = TemplateEngineType.FREEMARKER
    sourceFiles = fileTree("src/templates")
    templateData = TemplateData("test", mapOf("year" to "2014"))
    outputDir = file("$buildDir/genOutput3")

    inputs.property("engine", templateEngine)
    inputs.files(sourceFiles)
    inputs.property("templateData.name", templateData.name)
    inputs.property("templateData.variables", templateData.variables)
    outputs.dir(outputDir)
}
// end::custom-class-runtime-api[]

// tag::runtime-api-conf[]
tasks.register<ProcessTemplatesNoAnnotations>("processTemplatesRuntimeConf") {
    // ...
// end::runtime-api-conf[]
    templateEngine = TemplateEngineType.FREEMARKER
    templateData = TemplateData("test", mapOf("year" to "2014"))
    outputDir = file("$buildDir/genOutput3")
// tag::runtime-api-conf[]
    sourceFiles = fileTree("src/templates") {
        include("**/*.fm")
    }

    inputs.files(sourceFiles).skipWhenEmpty()
    // ...
// end::runtime-api-conf[]
    inputs.property("engine", templateEngine)
    inputs.property("templateData.name", templateData.name)
    inputs.property("templateData.variables", templateData.variables)
    outputs.dir(outputDir)
// tag::runtime-api-conf[]
}
// end::runtime-api-conf[]

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
