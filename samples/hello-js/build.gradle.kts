import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
    id("kotlin2js") version "1.3.20"
}

dependencies {
    compile(kotlin("stdlib-js"))
}

repositories {
    jcenter()
}

tasks {
    compileKotlin2Js {
        kotlinOptions {
            outputFile = "${sourceSets.main.get().output.resourcesDir}/output.js"
            sourceMap = true
        }
    }
    val unpackKotlinJsStdlib by registering {
        group = "build"
        description = "Unpack the Kotlin JavaScript standard library"
        val outputDir = file("$buildDir/$name")
        inputs.property("compileClasspath", configurations.compileClasspath.get())
        outputs.dir(outputDir)
        doLast {
            val kotlinStdLibJar = configurations.compileClasspath.get().single {
                it.name.matches(Regex("kotlin-stdlib-js-.+\\.jar"))
            }
            copy {
                includeEmptyDirs = false
                from(zipTree(kotlinStdLibJar))
                into(outputDir)
                include("**/*.js")
                exclude("META-INF/**")
            }
        }
    }
    val assembleWeb by registering(Copy::class) {
        group = "build"
        description = "Assemble the web application"
        includeEmptyDirs = false
        from(unpackKotlinJsStdlib)
        from(sourceSets.main.get().output) {
            exclude("**/*.kjsm")
        }
        into("$buildDir/web")
    }
    assemble {
        dependsOn(assembleWeb)
    }
}
