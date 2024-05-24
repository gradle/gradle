/*
 * Copyright 2020 the original author or authors.
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

import gradlebuild.configureAsJarClasspath

plugins {
    java
}

interface LaunchableJar {
    /**
     * The main class for the application. Can be undefined.
     */
    val mainClassName: Property<String>
}

val app = extensions.create<LaunchableJar>("app")

val manifestClasspath by configurations.creating {
    isTransitive = false

    configureAsJarClasspath(objects)
}

tasks.jar.configure {
    val classpath = manifestClasspath.elements.map { classpathDependency ->
        classpathDependency.joinToString(" ") { it.asFile.name }
    }
    manifest.attributes("Class-Path" to classpath)
    if (app.mainClassName.isPresent) {
        manifest.attributes("Main-Class" to app.mainClassName)
    }
}
