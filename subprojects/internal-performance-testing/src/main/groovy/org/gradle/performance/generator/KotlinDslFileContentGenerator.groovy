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

class KotlinDslFileContentGenerator extends FileContentGenerator {

    KotlinDslFileContentGenerator(TestProjectGeneratorConfiguration config) {
        super(config)
    }

    @Override
    protected String generateEnableFeaturePreviewCode() {
        return ""
    }

    @Override
    protected String missingJavaLibrarySupportFlag() {
        'val missingJavaLibrarySupport = GradleVersion.current() < GradleVersion.version("3.4")'
    }

    @Override
    protected String noJavaLibraryPluginFlag() {
        'val noJavaLibraryPlugin = hasProperty("noJavaLibraryPlugin")'
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
    protected String imperativelyApplyPlugin(String plugin) {
        "apply(plugin = \"$plugin\")"
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
    protected String configurationsIfMissingJavaLibrarySupport(boolean hasParent) {
        """
        if (missingJavaLibrarySupport) {
            configurations {
                ${hasParent ? '"api"()' : ''}
                "implementation"()
                "testImplementation"()
                ${hasParent ? '"compile" { extendsFrom(configurations["api"]) }' : ''}
                "compile" { extendsFrom(configurations["implementation"]) }
                "testCompile" { extendsFrom(configurations["testImplementation"]) }
            }
        } else if (noJavaLibraryPlugin) {
            configurations {
                ${hasParent ? '"api"()' : ''}
                ${hasParent ? '"compile" { extendsFrom(configurations["api"]) }' : ''}
            }
        }
        """
    }

    @Override
    protected String directDependencyDeclaration(String configuration, String notation) {
        notation.endsWith('()') ? "\"$configuration\"($notation)" : "\"$configuration\"(\"$notation\")"
    }

    @Override
    protected String projectDependencyDeclaration(String configuration, int projectNumber) {
        "\"$configuration\"(${dependency(projectNumber)})"
    }
}
