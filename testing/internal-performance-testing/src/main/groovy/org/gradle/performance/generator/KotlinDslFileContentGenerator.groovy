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
package org.gradle.performance.generator

import groovy.transform.CompileStatic

@CompileStatic
class KotlinDslFileContentGenerator extends FileContentGenerator {

    KotlinDslFileContentGenerator(TestProjectGeneratorConfiguration config) {
        super(config)
    }

    @Override
    protected String generateEnableFeaturePreviewCode() {
        return ""
    }


    @Override
    protected String tasksConfiguration() {
        """
        val compilerMemory: String by project
        val testRunnerMemory: String by project
        val testForkEvery: String by project

        tasks.withType<JavaCompile> {
            options.isFork = true
            options.isIncremental = true
            options.forkOptions.memoryInitialSize = compilerMemory
            options.forkOptions.memoryMaximumSize = compilerMemory
        }
        tasks.withType<GroovyCompile> {
            options.isFork = true
            options.isIncremental = true
            options.forkOptions.memoryInitialSize = compilerMemory
            options.forkOptions.memoryMaximumSize = compilerMemory
        }

        tasks.withType<Test> {
            ${config.useTestNG ? 'useTestNG()' : ''}
            minHeapSize = testRunnerMemory
            maxHeapSize = testRunnerMemory
            maxParallelForks = ${config.maxParallelForks}
            setForkEvery(testForkEvery.toLong())

            if (!JavaVersion.current().isJava8Compatible) {
                jvmArgs("-XX:MaxPermSize=512m")
            }
            jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        }

        task<DependencyReportTask>("dependencyReport") {
            outputs.upToDateWhen { false }
            outputFile = buildDir.resolve("dependencies.txt")
        }
        """
    }

    @Override
    protected String pluginBlockApply(String plugin) {
        "id(\"$plugin\")"
    }

    @Override
    protected String createTaskThatDependsOnAllIncludedBuildsTaskWithSameName(String taskName) {
        """
        task("$taskName") {
            dependsOn(gradle.includedBuilds.map { it.task(":$taskName") })
        }
        """
    }


    @Override
    protected String versionCatalogDependencyDeclaration(String configuration, String alias) {
        "\"$configuration\"(libs.$alias)"
    }

    @Override
    protected String projectDependencyDeclaration(String configuration, int projectNumber) {
        "\"$configuration\"(${dependency(projectNumber)})"
    }
}
