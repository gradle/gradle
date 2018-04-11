import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    application
}

val androidTools by configurations.creating
configurations.compile.extendsFrom(androidTools)

repositories {
    google()
}

dependencies {
    compile(project(":toolingApi"))
    androidTools("com.android.tools.build:gradle:3.0.0")
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

application {
    mainClassName = "org.gradle.performance.android.Main"
    applicationName = "android-test-app"
}

tasks {
    "buildClassPath"(BuildClassPath::class) {
        val jar: Jar by getting
        dependsOn(jar)
        classpath = androidTools + layout.filesFor(jar.archivePath)
        outputFile = buildDir.resolve("classpath.txt")
    }

    val distZip: Zip by getting
    val distTar: Tar by getting
    listOf(distZip, distTar).forEach { it.baseName = "android-test-app" }
    project(":distributions").tasks["buildDists"].dependsOn(distZip)
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
