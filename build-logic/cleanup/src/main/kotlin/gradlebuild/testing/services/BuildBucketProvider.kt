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

package gradlebuild.testing.services

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.util.GradleVersion
import java.io.StringReader
import java.util.Properties


abstract class BuildBucketProvider : BuildService<BuildBucketProvider.Params> {

    interface Params : BuildServiceParameters {
        val includeTestClasses: Property<String>
        val excludeTestClasses: Property<String>
        val onlyTestGradleVersion: Property<String>
        val repoRoot: DirectoryProperty
    }

    val bucketProvider = when {
        parameters.includeTestClasses.get().isNotBlank() -> {
            val content = parameters.repoRoot.file("test-splits/include-test-classes.properties").get().asFile.readText()
            println("Tests to be included:\n$content")
            IncludeTestClassProvider(readTestClasses(content))
        }
        parameters.excludeTestClasses.get().isNotBlank() -> {
            val content = parameters.repoRoot.file("test-splits/exclude-test-classes.properties").get().asFile.readText()
            println("Tests to be excluded:\n$content")
            ExcludeTestClassProvider(readTestClasses(content))
        }
        parameters.onlyTestGradleVersion.get().isNotBlank() -> {
            CrossVersionBucketProvider(parameters.onlyTestGradleVersion.get())
        }
        else -> {
            NoOpTestClassProvider()
        }
    }

    private
    fun readTestClasses(content: String): Map<String, List<String>> {
        val properties = Properties()
        val ret = mutableMapOf<String, MutableList<String>>()
        properties.load(StringReader(content))
        properties.forEach { key, value ->
            val list = ret.getOrDefault(value, mutableListOf())
            list.add(key!!.toString())
            ret[value!!.toString()] = list
        }
        return ret
    }


    interface BuildBucketProvider {
        fun configureTest(testTask: Test, sourceSetName: String)
    }

    // -PonlyTestGradleVersion=4.0-5.0
    // 4.0 <= gradle < 5.0
    class CrossVersionBucketProvider(onlyTestGradleVersion: String) : BuildBucketProvider {
        val startVersionInclusive = onlyTestGradleVersion.substringBefore("-")
        val endVersionExclusive = onlyTestGradleVersion.substringAfter("-")

        override fun configureTest(testTask: Test, sourceSetName: String) {
            val currentVersionUnderTest = extractTestTaskGradleVersion(testTask.name)
            currentVersionUnderTest?.apply {
                testTask.enabled = currentVersionEnabled(currentVersionUnderTest)
            }
        }

        private
        fun currentVersionEnabled(currentVersionUnderTest: String): Boolean {
            val versionUnderTest = GradleVersion.version(currentVersionUnderTest).baseVersion
            return GradleVersion.version(startVersionInclusive) <= versionUnderTest
                && versionUnderTest < GradleVersion.version(endVersionExclusive)
        }

        private
        fun extractTestTaskGradleVersion(name: String): String? = "gradle(.+)CrossVersionTest".toRegex().find(name)?.groupValues?.get(1)
    }

    class IncludeTestClassProvider(private val includeTestClasses: Map<String, List<String>>) : BuildBucketProvider {
        override fun configureTest(testTask: Test, sourceSetName: String) {
            testTask.filter.isFailOnNoMatchingTests = false
            val classesForSourceSet = includeTestClasses[sourceSetName]
            if (classesForSourceSet == null) {
                // No classes included, disable
                testTask.enabled = false
            } else {
                testTask.filter.includePatterns.addAll(classesForSourceSet)
            }
        }
    }

    class ExcludeTestClassProvider(private val excludeTestClasses: Map<String, List<String>>) : BuildBucketProvider {
        override fun configureTest(testTask: Test, sourceSetName: String) {
            testTask.filter.isFailOnNoMatchingTests = false
            excludeTestClasses[sourceSetName]?.apply { testTask.filter.excludePatterns.addAll(this) }
        }
    }

    class NoOpTestClassProvider : BuildBucketProvider {
        override fun configureTest(testTask: Test, sourceSetName: String) {
        }
    }
}
