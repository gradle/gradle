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
import gradlebuild.configureAsRuntimeJarClasspath

plugins {
    java
}

val agentsClasspath = configurations.dependencyScope("agentsClasspath")
val resolveAgentsClasspath = configurations.resolvable("resolveAgentsClasspath") {
    extendsFrom(agentsClasspath.get())
    configureAsRuntimeJarClasspath(objects)
}

val startScripts = tasks.register<GradleStartScriptGenerator>("startScripts") {
    startScriptsDir = layout.buildDirectory.dir("startScripts")
    launcherJar.from(tasks.jar)
    agentJars.from(resolveAgentsClasspath)
    // The trick below is to use the templates from the current code instead of the wrapper. It does not cover the case where the generation logic is updated though.
    // TODO uncomment following two lines after wrapper upgrade. See #35693
    //unixScriptTemplate.from(layout.projectDirectory.file("../../../platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt"))
    //windowsScriptTemplate.from(layout.projectDirectory.file("../../../platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/windowsStartScript.txt"))
    // TODO remove the following two lines after wrapper upgrade. See #35693
    unixScriptTemplate.from(resources.text.fromUri(javaClass.getResource("/org/gradle/api/internal/plugins/unixStartScript.txt")!!.toURI()).asFile())
    windowsScriptTemplate.from(resources.text.fromUri(javaClass.getResource("/org/gradle/api/internal/plugins/windowsStartScript.txt")!!.toURI()).asFile())
}

configurations {
    create("gradleScriptsElements") {
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named("start-scripts"))
        outgoing.artifact(startScripts)
    }
}
