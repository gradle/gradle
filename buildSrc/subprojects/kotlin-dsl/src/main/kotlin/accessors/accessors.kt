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
package accessors

import org.gradle.api.Project

import org.gradle.api.file.SourceDirectorySet

import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaPluginConvention

import org.gradle.api.reporting.ReportingExtension

import org.gradle.api.tasks.GroovySourceSet
import org.gradle.api.tasks.SourceSet

import org.gradle.plugins.ide.eclipse.model.EclipseModel

import org.gradle.kotlin.dsl.*


val Project.base
    get() = the<BasePluginConvention>()


val Project.java
    get() = the<JavaPluginConvention>()


val Project.reporting
    get() = the<ReportingExtension>()


val SourceSet.groovy: SourceDirectorySet
    get() = withConvention(GroovySourceSet::class) { groovy }


fun Project.eclipse(configure: EclipseModel.() -> Unit): Unit =
    extensions.configure("eclipse", configure)
