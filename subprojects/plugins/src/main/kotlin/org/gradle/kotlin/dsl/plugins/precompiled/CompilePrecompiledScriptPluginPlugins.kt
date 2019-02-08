/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction


@CacheableTask
open class CompilePrecompiledScriptPluginPlugins : ClassPathSensitiveTask() {

    @get:OutputDirectory
    var outputDir = project.objects.directoryProperty()

    @TaskAction
    fun compile() {
        outputDir.withOutputDirectory {
        }
    }

    fun sourceDir(dir: Provider<Directory>) = Unit
}
