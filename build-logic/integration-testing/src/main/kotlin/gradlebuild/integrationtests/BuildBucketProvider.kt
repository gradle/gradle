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

package gradlebuild.integrationtests

import gradlebuild.basics.kotlindsl.stringPropertyOrEmpty

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import org.gradle.util.GradleVersion
import java.io.StringReader
import java.util.Properties


fun Project.bucketProvider(): BuildBucketProvider {
    if (!rootProject.extra.has("bucketProvider")) {
        rootProject.extra["bucketProvider"] = when {
            project.stringPropertyOrEmpty("includeTestClasses").isNotBlank() -> {
                val content = project.rootProject.file("test-splits/include-test-classes.properties").readText()
                println("Tests to be included:\n$content")
                IncludeTestClassProvider(readTestClasses(content))
            }
            project.stringPropertyOrEmpty("excludeTestClasses").isNotBlank() -> {
                val content = project.rootProject.file("test-splits/exclude-test-classes.properties").readText()
                println("Tests to be excluded:\n$content")
                ExcludeTestClassProvider(readTestClasses(content))
            }
            project.stringPropertyOrEmpty("onlyTestGradleVersion").isNotBlank() -> {
                CrossVersionBucketProvider(project.stringPropertyOrEmpty("onlyTestGradleVersion"))
            }
            else -> {
                NoOpTestClassProvider()
            }
        }
    }
    return rootProject.extra["bucketProvider"] as BuildBucketProvider
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
    fun configureTest(testTask: Test, sourceSet: SourceSet, testType: TestType)
}


// -PonlyTestGradleVersion=4.0-5.0
// 4.0 <= gradle < 5.0
class CrossVersionBucketProvider(private val onlyTestGradleVersion: String) : BuildBucketProvider {
    val startVersionInclusive = onlyTestGradleVersion.substringBefore("-")
    val endVersionExclusive = onlyTestGradleVersion.substringAfter("-")

    override fun configureTest(testTask: Test, sourceSet: SourceSet, testType: TestType) {
        val currentVersionUnderTest = extractTestTaskGradleVersion(testTask.name)
        currentVersionUnderTest?.apply {
            testTask.enabled = currentVersionEnabled(currentVersionUnderTest)
        }
    }

    private
    fun currentVersionEnabled(currentVersionUnderTest: String): Boolean {
        val versionUnderTest = GradleVersion.version(currentVersionUnderTest)
        return GradleVersion.version(startVersionInclusive) <= versionUnderTest
            && versionUnderTest < GradleVersion.version(endVersionExclusive)
    }

    private
    fun extractTestTaskGradleVersion(name: String): String? = "gradle(.+)CrossVersionTest".toRegex().find(name)?.groupValues?.get(1)
}


class IncludeTestClassProvider(private val includeTestClasses: Map<String, List<String>>) : BuildBucketProvider {
    override fun configureTest(testTask: Test, sourceSet: SourceSet, testType: TestType) {
        testTask.filter.isFailOnNoMatchingTests = false
        val classesForSourceSet = includeTestClasses[sourceSet.name]
        if (classesForSourceSet == null) {
            // No classes included, disable
            testTask.enabled = false
        } else {
            testTask.filter.includePatterns.addAll(classesForSourceSet)
        }
    }
}


class ExcludeTestClassProvider(private val excludeTestClasses: Map<String, List<String>>) : BuildBucketProvider {
    override fun configureTest(testTask: Test, sourceSet: SourceSet, testType: TestType) {
        testTask.filter.isFailOnNoMatchingTests = false
        excludeTestClasses[sourceSet.name]?.apply { testTask.filter.excludePatterns.addAll(this) }
    }
}


class NoOpTestClassProvider : BuildBucketProvider {
    override fun configureTest(testTask: Test, sourceSet: SourceSet, testType: TestType) {
    }
}
