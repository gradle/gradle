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
@file:Suppress("deprecation")
package gradlebuild.basics.accessors

import org.gradle.api.file.SourceDirectorySet

import org.gradle.api.tasks.GroovySourceSet
import org.gradle.api.tasks.SourceSet

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

import org.gradle.kotlin.dsl.*


// TODO these accessors should be generated - https://github.com/gradle/gradle/issues/3191


val SourceSet.groovy: SourceDirectorySet
    get() = withConvention(GroovySourceSet::class) { groovy }


val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }
