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

import gradlebuild.classycle.extension.ClassycleExtension
import gradlebuild.classycle.tasks.Classycle

plugins {
    java
}

val classycleBaseName = "classycle"

val extension = extensions.create<ClassycleExtension>(classycleBaseName)
extension.excludePatterns.convention(emptyList())
extension.reportResourcesZip.set(project.rootProject.file("config/classycle_report_resources.zip"))

configurations.create(classycleBaseName)
dependencies.add(classycleBaseName, "classycle:classycle:1.4.2@jar")
val classycle = tasks.register("classycle")
sourceSets.all {
    val taskName = getTaskName("classycle", null)
    val sourceSetTask = tasks.register(
        taskName,
        Classycle::class.java,
        output.classesDirs,
        extension.excludePatterns,
        name,
        reporting.file("classycle"),
        extension.reportResourcesZip
    )
    classycle { dependsOn(sourceSetTask) }
    tasks.named("check") { dependsOn(sourceSetTask) }
}
