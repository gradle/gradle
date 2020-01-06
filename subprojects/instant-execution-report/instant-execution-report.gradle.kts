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

plugins {
    id("kotlin2js")
}

dependencies {
    compileOnly(kotlin("stdlib-js"))
}

tasks {

    compileKotlin2Js {
        kotlinOptions {
            outputFile = "$buildDir/js/instant-execution-report.js"
            metaInfo = false
            sourceMap = false
        }
    }

    val unpackKotlinJsStdlib by registering(Copy::class) {
        group = "build"
        description = "Unpacks the Kotlin JavaScript standard library"

        val kotlinStdLibJsJar = configurations.compileClasspath.map { compileClasspath ->
            val kotlinStdlibJsJarRegex = Regex("kotlin-stdlib-js-.+\\.jar")
            compileClasspath.single { file -> file.name.matches(kotlinStdlibJsJarRegex) }
        }

        from(kotlinStdLibJsJar.map(::zipTree)) {
            include("**/*.js")
            exclude("META-INF/**")
        }

        into("$buildDir/$name")

        includeEmptyDirs = false
    }

    val assembleReport by registering(Copy::class) {
        from(processResources)
        from(unpackKotlinJsStdlib)
        from(compileKotlin2Js.map { it.outputFile })
        into("$buildDir/report")
    }

    assemble {
        dependsOn(assembleReport)
    }
}
