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

package accessors

import org.gradle.api.Project

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the

import com.gradle.publish.PluginBundleExtension

import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension


internal
val Project.sourceSets
    get() = the<SourceSetContainer>()


internal
fun Project.publishing(action: PublishingExtension.() -> Unit) =
    configure(action)


internal
fun Project.kotlin(action: KotlinProjectExtension.() -> Unit) =
    configure(action)


internal
fun Project.gradlePlugin(action: GradlePluginDevelopmentExtension.() -> Unit) =
    configure(action)


internal
fun Project.pluginBundle(action: PluginBundleExtension.() -> Unit) =
    configure(action)
