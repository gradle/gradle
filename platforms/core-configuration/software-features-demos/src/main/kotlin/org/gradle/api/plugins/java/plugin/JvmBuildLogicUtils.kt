/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins.java.plugin

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.internal.plugins.SoftwareFeatureApplicationContext
import org.gradle.api.plugins.java.HasCompiledBytecode
import org.gradle.api.plugins.java.HasJarFile
import org.gradle.api.plugins.java.HasProcessedResources
import org.gradle.api.plugins.java.HasResources
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.util.internal.TextUtil.capitalize
import kotlin.jvm.java

internal fun <T> SoftwareFeatureApplicationContext.registerJar(
    mainClasses: NamedDomainObjectProvider<T>,
    model: HasJarFile
) where T: HasProcessedResources, T : HasCompiledBytecode {
    val jarTask = project.tasks.register("jar", Jar::class.java) { task ->
        task.from(mainClasses.map { it.byteCodeDir })
        task.from(mainClasses.map { it.processedResourcesDir })
    }

    model.jarFile.set(jarTask.map { it.archiveFile.get() })
}

internal fun <T> SoftwareFeatureApplicationContext.registerResourcesProcessing(source: T): TaskProvider<Copy> where T : Named, T : HasResources {
    val processResourcesTask = project.tasks.register("process" + capitalize(source.name) + "Resources", Copy::class.java) { task ->
        task.group = LifecycleBasePlugin.BUILD_GROUP
        task.description = "Processes the ${source.name} resources."
        task.from(source.resources.asFileTree)
    }
    return processResourcesTask
}
