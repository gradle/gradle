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
package org.gradle.plugins.classycle

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty

import org.gradle.kotlin.dsl.*

open class ClassycleExtension(project: Project) {

    val excludePatterns: ListProperty<String> = project.objects.listProperty()

    val reportResourcesZip: RegularFileProperty = project.layout.fileProperty().also {
        it.set(project.rootProject.file("gradle/classycle_report_resources.zip"))
    }
}
