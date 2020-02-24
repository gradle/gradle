import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    application
}

val androidTools by configurations.creating
configurations.implementation { extendsFrom(androidTools) }

repositories {
    google()
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":toolingApi"))
    androidTools("com.android.tools.build:gradle:3.0.0")
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

application {
    mainClassName = "org.gradle.performance.android.Main"
    applicationName = "android-test-app"
}

tasks.register<BuildClassPath>("buildClassPath") {
    val jar: Jar by tasks
    classpath.from(androidTools)
    classpath.from(jar.archiveFile)
    outputFile.set(project.layout.buildDirectory.file("classpath.txt"))
}

listOf(tasks.distZip, tasks.distTar).forEach {
    it { archiveBaseName.set("android-test-app") }
}

tasks.register("buildDists") {
    dependsOn(tasks.distZip)
}

open class BuildClassPath : DefaultTask() {
    @get:InputFiles
    val classpath: ConfigurableFileCollection = project.files()

    @get:OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun buildClasspath() =
        outputFile.get().getAsFile().printWriter().use { wrt ->
            classpath.asFileTree.files.forEach {
                wrt.println(it.absolutePath)
            }
        }
}
