import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath(kotlin("gradle-plugin"))
    }
}

apply {
    plugin("kotlin2js")
}

dependencies {
    "compile"(kotlin("stdlib-js"))
}

repositories {
    jcenter()
}

tasks.withType<Kotlin2JsCompile> {
    kotlinOptions.outputFile = "$buildDir/web/output.js"
    kotlinOptions.sourceMap = true
}

val assembleWeb by tasks.creating
assembleWeb.doLast {
    configurations["compileClasspath"].forEach { file: File ->
        copy {
            includeEmptyDirs = false

            from(zipTree(file.absoluteFile))
            into("$buildDir/web")
            include { fileTreeElement ->
                val path = fileTreeElement.path
                path.endsWith(".js") && (path.startsWith("META-INF/resources/") || !path.startsWith("META-INF/"))
            }
        }
        copy {
            from("${the<JavaPluginConvention>().sourceSets["main"].output.resourcesDir}/index.html")
            into("$buildDir/web")
        }
    }
}

val assemble by tasks
val classes by tasks
assemble.dependsOn(assembleWeb)
assembleWeb.dependsOn(classes)
