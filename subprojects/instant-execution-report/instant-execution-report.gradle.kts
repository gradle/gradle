/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("kotlin2js")
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

dependencies {
    compileOnly(kotlin("stdlib-js"))
}

val reportResources by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("report-resources"))
    }
    outgoing.artifact(
        tasks.processResources.map { it.destinationDir }
    )
}

tasks {

    compileKotlin2Js {
        kotlinOptions {
            outputFile = "$buildDir/js/instant-execution-report.js"
            metaInfo = false
            sourceMap = false
            freeCompilerArgs = listOf(
                "-XXLanguage:+NewInference"
            )
        }
    }

    val unpackKotlinJsStdlib by registering {
        group = "build"
        description = "Unpacks the Kotlin JavaScript standard library"
        val outputDir = file("$buildDir/$name")
        inputs.property("compileClasspath", configurations.compileClasspath)
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

    processResources {
        from(compileKotlin2Js.map { it.outputFile })
        from(unpackKotlinJsStdlib)
    }
}
