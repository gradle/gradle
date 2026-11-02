/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.jvm.argumentproviders

import org.gradle.api.Named
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import javax.inject.Inject


/**
 * Contributes `<prefix><value>` to a forked JVM's arguments when [value] is present.
 *
 * Unlike a direct `systemProperty` or `jvmArgs` call, the value is read at execution and stays a task input.
 * Gradle applies argument providers after `systemProperties` and `maxHeapSize`, so this value wins over those.
 */
abstract class LazyJvmArgumentProvider @Inject constructor(
    private val name: String,
    private val prefix: String
) : CommandLineArgumentProvider, Named {

    @get:Input
    @get:Optional
    abstract val value: Property<String>

    @Internal
    override fun getName() = name

    override fun asArguments(): Iterable<String> = value.map { listOf(prefix + it) }.getOrElse(emptyList())
}


/**
 * The lazy value of system property [key] for this task's JVM, registering its provider on first use.
 */
fun <T> T.lazySystemProperty(key: String): Property<String> where T : Task, T : JavaForkOptions =
    lazyJvmArgument(key, "-D$key=")


private
fun <T> T.lazyJvmArgument(name: String, prefix: String): Property<String> where T : Task, T : JavaForkOptions {
    val existing = jvmArgumentProviders.filterIsInstance<LazyJvmArgumentProvider>().find { it.name == name }
    val provider = existing ?: project.objects.newInstance<LazyJvmArgumentProvider>(name, prefix).also { jvmArgumentProviders.add(it) }
    return provider.value
}
