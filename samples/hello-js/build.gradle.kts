import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

buildscript {
    repositories {
        mavenCentral()
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
    kotlinOptions.outputFile = "$projectDir/web/output.js"
    kotlinOptions.moduleKind = "plain"
    kotlinOptions.sourceMap = true
}

val build by tasks
build.doLast {
    configurations["compile"].forEach { file: File ->
        copy {
            includeEmptyDirs = false

            from(zipTree(file.absoluteFile))
            into("$projectDir/web")
            include { fileTreeElement ->
                val path = fileTreeElement.path
                path.endsWith(".js") && (path.startsWith("META-INF/resources/") || !path.startsWith("META-INF/"))
            }
        }
    }
}
