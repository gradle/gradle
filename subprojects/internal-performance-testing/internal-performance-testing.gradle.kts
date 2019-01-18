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
    gradlebuild.classycle
}

val reports by configurations.creating
val flamegraph by configurations.creating
configurations.compileOnly { extendsFrom(flamegraph) }

repositories {
    javaScript.googleApis()
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
    compile(library("commons_math"))
    compile(library("jcl_to_slf4j"))
    compile("org.openjdk.jmc:flightrecorder:7.0.0-SNAPSHOT")

    runtime("com.h2database:h2:1.4.192")
}

gradlebuildJava {
    moduleType = ModuleType.REQUIRES_JAVA_8
}

val generatedResourcesDir = gradlebuildJava.generatedResourcesDir

val reportResources = tasks.register<Copy>("reportResources") {
    from(reports)
    into("$generatedResourcesDir/org/gradle/reporting")
}

java.sourceSets.main { output.dir(mapOf("builtBy" to reportResources), generatedResourcesDir) }

tasks.jar {
    inputs.files(flamegraph)
    from(files(deferred{ flamegraph.map { zipTree(it) } }))
}

testFixtures {
    from(":core", "main")
    from(":toolingApi", "main")
}