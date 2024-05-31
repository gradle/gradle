import gradlebuild.integrationtests.tasks.IntegrationTest

/*
 * Copyright 2024 the original author or authors.
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

plugins {
    // TODO Can we apply less here?
    id("gradlebuild.internal.java")

//    id("gradlebuild.repositories")
//    id("gradlebuild.integration-tests")
}

val testRepo = configurations.dependencyScope("testRepo")
val resolveTestRepo = configurations.resolvable("resolveTestRepo") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named("gradle-local-repository"))
        attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EMBEDDED))
    }
    extendsFrom(testRepo.get())
}

dependencies {
    testRepo(project(":public-api"))
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

abstract class ApiJarTestRepoLocationCommandLineArgumentProvider() : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repoLocation: DirectoryProperty

    override fun asArguments() =
        listOf("-DintegTest.apiJarRepoLocation=${repoLocation.get().asFile.absolutePath}")
}

tasks.withType<IntegrationTest>() {
    val argument = objects.newInstance(ApiJarTestRepoLocationCommandLineArgumentProvider::class.java).apply {
        repoLocation.fileProvider(resolveTestRepo.flatMap { it.elements }.map { it.first().asFile })
    }
    jvmArgumentProviders.add(argument)
}
