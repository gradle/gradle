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

tasks.register("buildClassPath", BuildClassPath::class.java) {
    val jar: Jar = tasks.withType(Jar::class.java).getByName("jar")
    dependsOn(jar)
    classpath = androidTools + files(jar.archivePath)
    outputFile = buildDir.resolve("classpath.txt")
}

val distZip: TaskProvider<Zip> = tasks.withType(Zip::class.java).named("distZip")
val distTar: TaskProvider<Tar> = tasks.withType(Tar::class.java).named("distTar")
listOf(distZip, distTar).forEach { it.configure { baseName = "android-test-app" } }
project(":distributions").tasks.register("buildDists").configure { dependsOn(distZip) }


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
