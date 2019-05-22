import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    application
}

val androidTools by configurations.creating
configurations.compile { extendsFrom(androidTools) }

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
    dependsOn(jar)
    classpath = androidTools + files(jar.archivePath)
    outputFile = buildDir.resolve("classpath.txt")
}

listOf(tasks.distZip, tasks.distTar).forEach {
    it { baseName = "android-test-app" }
}

project(":distributions").tasks.register("buildDists") {
    dependsOn(tasks.distZip)
}


open class BuildClassPath : DefaultTask() {

    @InputFiles
    lateinit var classpath: FileCollection

    @OutputFile
    lateinit var outputFile: File

    @TaskAction
    fun buildClasspath() =
        outputFile.printWriter().use { wrt ->
            classpath.asFileTree.files.forEach {
                wrt.println(it.absolutePath)
            }
        }
}
