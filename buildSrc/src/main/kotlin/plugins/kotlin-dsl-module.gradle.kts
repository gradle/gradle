/*
 * Copyright 2018 the original author or authors.
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

/*
 * Configures a Gradle Kotlin DSL module.
 *
 * The assembled jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 */

import accessors.java

import build.kotlinDslDebugPropertyName
import build.withTestStrictClassLoading
import build.withTestWorkersMemoryLimits


apply(plugin = "kotlin-library")

// including all sources
val mainSourceSet = java.sourceSets["main"]
afterEvaluate {
    tasks.getByName("jar") {
        this as Jar
        from(mainSourceSet.allSource)
        manifest.attributes.apply {
            put("Implementation-Title", "Gradle Kotlin DSL (${project.name})")
            put("Implementation-Version", version)
        }
    }
}

// sets the Gradle Test Kit user home into custom installation build dir
if (hasProperty(kotlinDslDebugPropertyName) && findProperty(kotlinDslDebugPropertyName) != "false") {
    tasks.withType<Test> {
        systemProperty(
            "org.gradle.testkit.dir",
            "${rootProject.buildDir}/custom/test-kit-user-home")
    }
}

withTestStrictClassLoading()
withTestWorkersMemoryLimits()
