/*
 * Copyright 2016 the original author or authors.
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
import accessors.*

plugins {
    `javascript-base`
    id("gradlebuild.classycle")
}

val reports by configurations.creating
val flamegraph by configurations.creating
configurations.compileOnly.extendsFrom(flamegraph)

repositories {
    javaScript.googleApis()
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

dependencies {
    reports("jquery:jquery.min:1.11.0@js")
    reports("flot:flot:0.8.1:min@js")

    compile(library("groovy"))
    compile(project(":baseServices"))
    compile(library("slf4j_api"))
    compile(project(":internalIntegTesting"))
    compile(library("jatl"))
    compile(library("jgit"))
    compile(library("commons_httpclient"))
    compile(library("jsch"))

    flamegraph("com.github.oehme:jfr-flame-graph:v0.0.6:all")

    runtime("com.h2database:h2:1.4.192")
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

val generatedResourcesDir = gradlebuildJava.generatedResourcesDir

val reportResources by tasks.creating(Copy::class) {
    from(reports)
    into("$generatedResourcesDir/org/gradle/reporting")
}

java.sourceSets["main"].output.dir(mapOf("builtBy" to reportResources), generatedResourcesDir)

tasks {
    "jar"(Jar::class) {
        inputs.files(flamegraph)
        from(files(deferred{ flamegraph.map { zipTree(it) } }))
    }
}

testFixtures {
    from(":core", "main")
    from(":toolingApi", "main")
}

ideConfiguration {
    makeAllSourceDirsTestSourceDirsToWorkaroundIssuesWithIDEA13()
}
