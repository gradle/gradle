/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.gradlebuild.java.BuildJvms
import org.gradle.gradlebuild.unittestandcompile.UnitTestAndCompileExtension
import org.gradle.kotlin.dsl.*


// This file contains Kotlin extensions for the gradle/gradle build


fun Project.gradlebuildJava(configure: UnitTestAndCompileExtension.() -> Unit): Unit =
    extensions.configure("gradlebuildJava", configure)


val Project.buildJvms
    get() = extensions.getByType<BuildJvms>()


val Project.gradlebuildJava
    get() = extensions.getByName<UnitTestAndCompileExtension>("gradlebuildJava")


fun RepositoryHandler.googleApisJs() {
    ivy {
        name = "googleApisJs"
        setUrl("https://ajax.googleapis.com/ajax/libs")
        patternLayout {
            artifact("[organization]/[revision]/[module].[ext]")
            ivy("[organization]/[revision]/[module].xml")
        }
        metadataSources {
            artifact()
        }
    }
}
