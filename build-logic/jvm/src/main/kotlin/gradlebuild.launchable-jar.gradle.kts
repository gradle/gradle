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

import gradlebuild.startscript.tasks.GradleStartScriptGenerator

plugins {
    java
}

val manifestClasspath by configurations.creating {
    isTransitive = false

    configureAsJarClasspath()
}

val agentsClasspath by configurations.creating {
    configureAsJarClasspath()
}

fun Configuration.configureAsJarClasspath() {
    isCanBeResolved = true
    isCanBeConsumed = false

    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

tasks.jar.configure {
    val classpath = manifestClasspath.elements.map { classpathDependency ->
        classpathDependency.joinToString(" ") {
            it.asFile.name
        }
    }
    manifest.attributes("Class-Path" to classpath)
    manifest.attributes("Main-Class" to "org.gradle.launcher.GradleMain")
}


val startScripts = tasks.register<GradleStartScriptGenerator>("startScripts") {
    startScriptsDir = layout.buildDirectory.dir("startScripts")
    launcherJar.from(tasks.jar)
    agentJars.from(agentsClasspath)
    // The trick below is to use the templates from the current code instead of the wrapper. It does not cover the case where the generation logic is updated though.
    unixScriptTemplate.from(layout.projectDirectory.file("../plugins/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt"))
    windowsScriptTemplate.from(layout.projectDirectory.file("../plugins/src/main/resources/org/gradle/api/internal/plugins/windowsStartScript.txt"))
}

configurations {
    create("gradleScriptsElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named("start-scripts"))
        outgoing.artifact(startScripts)
    }
}
