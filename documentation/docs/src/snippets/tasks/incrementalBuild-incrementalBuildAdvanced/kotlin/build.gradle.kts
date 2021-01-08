import org.example.*

// tag::failed-inferred-task-dep[]
plugins {
    // end::failed-inferred-task-dep[]
    base
// tag::failed-inferred-task-dep[]
    id("java-library")
}
// end::failed-inferred-task-dep[]

repositories {
    mavenCentral()
}

dependencies {
    implementation(group = "commons-collections", name = "commons-collections", version = "3.2.2")
    testImplementation(group = "junit", name = "junit", version = "4.+")
}

// tag::custom-task-class[]
tasks.register<ProcessTemplates>("processTemplates") {
    templateEngine.set(TemplateEngineType.FREEMARKER)
    templateData.name.set("test")
    templateData.variables.set(mapOf("year" to "2012"))
    outputDir.set(file(layout.buildDirectory.dir("genOutput")))

    sources(fileTree("src/templates"))
}
// end::custom-task-class[]

// tag::task-arg-method[]
val copyTemplates by tasks.registering(Copy::class) {
    into(file(layout.buildDirectory.dir("tmp")))
    from("src/templates")
}

tasks.register<ProcessTemplates>("processTemplates2") {
    // ...
// end::task-arg-method[]
    templateEngine.set(TemplateEngineType.FREEMARKER)
    templateData.name.set("test")
    templateData.variables.set(mapOf("year" to "2012"))
    outputDir.set(file(layout.buildDirectory.dir("genOutput")))
// tag::task-arg-method[]
    sources(copyTemplates)
}
// end::task-arg-method[]

// tag::failed-inferred-task-dep[]

tasks.register<Instrument>("badInstrumentClasses") {
    classFiles.from(fileTree(tasks.compileJava.map { it.destinationDir }))
    destinationDir.set(file(layout.buildDirectory.dir("instrumented")))
}
// end::failed-inferred-task-dep[]

// tag::inferred-task-dep[]
tasks.register<Instrument>("instrumentClasses") {
    classFiles.from(tasks.compileJava.map { it.outputs.files })
    destinationDir.set(file(layout.buildDirectory.dir("instrumented")))
}
// end::inferred-task-dep[]

// tag::inferred-task-dep-with-files[]
tasks.register<Instrument>("instrumentClasses2") {
    classFiles.from(layout.files(tasks.compileJava))
    destinationDir.set(file(layout.buildDirectory.dir("instrumented")))
}
// end::inferred-task-dep-with-files[]

// tag::inferred-task-dep-with-builtby[]
tasks.register<Instrument>("instrumentClassesBuiltBy") {
    classFiles.from(fileTree(tasks.compileJava.map { it.destinationDir }) {
        builtBy(tasks.compileJava)
    })
    destinationDir.set(file(layout.buildDirectory.dir("instrumented")))
}
// end::inferred-task-dep-with-builtby[]

// tag::up-to-date-when[]
tasks.register<Instrument>("alwaysInstrumentClasses") {
    classFiles.from(layout.files(tasks.compileJava))
    destinationDir.set(file(layout.buildDirectory.dir("instrumented")))
    outputs.upToDateWhen { false }
}
// end::up-to-date-when[]

tasks.build {
    dependsOn("processTemplates", "processTemplates2")
}
