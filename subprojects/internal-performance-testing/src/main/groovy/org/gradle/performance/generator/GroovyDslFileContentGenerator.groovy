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

class GroovyDslFileContentGenerator extends FileContentGenerator {

    GroovyDslFileContentGenerator(TestProjectGeneratorConfiguration config) {
        super(config)
    }

    @Override
    protected String generateEnableFeaturePreviewCode() {
        return """def enableFeaturePreviewSafe(String feature) {
                     try {
                        enableFeaturePreview(feature)
                        println "Enabled feature preview " + feature
                     } catch(Exception ignored) {
                        println "Failed to enable feature preview " + feature
                     }
                }

                ${config.featurePreviews.collect { "enableFeaturePreviewSafe(\"$it\")" }.join("\n")}
            """
    }

    @Override
    protected String missingJavaLibrarySupportFlag() {
        "def missingJavaLibrarySupport = GradleVersion.current() < GradleVersion.version('3.4')"
    }

    @Override
    protected String noJavaLibraryPluginFlag() {
        "def noJavaLibraryPlugin = hasProperty('noJavaLibraryPlugin')"
    }

    @Override
    protected String tasksConfiguration() {
        """
        String compilerMemory = getProperty('compilerMemory')
        String testRunnerMemory = getProperty('testRunnerMemory')
        int testForkEvery = getProperty('testForkEvery') as Integer
        List<String> javaCompileJvmArgs = findProperty('javaCompileJvmArgs')?.tokenize(';') ?: []

        tasks.withType(AbstractCompile) {
            options.fork = true
            options.incremental = true
            options.forkOptions.memoryInitialSize = compilerMemory
            options.forkOptions.memoryMaximumSize = compilerMemory
            options.forkOptions.jvmArgs.addAll(javaCompileJvmArgs)
        }

        tasks.withType(GroovyCompile) {
            groovyOptions.fork = true
            groovyOptions.forkOptions.memoryInitialSize = compilerMemory
            groovyOptions.forkOptions.memoryMaximumSize = compilerMemory
            groovyOptions.forkOptions.jvmArgs.addAll(javaCompileJvmArgs)
        }
        
        tasks.withType(Test) {
            ${config.useTestNG ? 'useTestNG()' : ''}
            minHeapSize = testRunnerMemory
            maxHeapSize = testRunnerMemory
            maxParallelForks = ${config.maxParallelForks}
            forkEvery = testForkEvery
            
            if (!JavaVersion.current().java8Compatible) {
                jvmArgs '-XX:MaxPermSize=512m'
            }
            jvmArgs '-XX:+HeapDumpOnOutOfMemoryError'
        }

        task dependencyReport(type: DependencyReportTask) {
            outputs.upToDateWhen { false }
            outputFile = new File(buildDir, "dependencies.txt")
        }
        """
    }

    @Override
    protected String imperativelyApplyPlugin(String plugin) {
        "apply plugin: '$plugin'"
    }

    @Override
    protected String createTaskThatDependsOnAllIncludedBuildsTaskWithSameName(String taskName) {
        """
        task $taskName {
            dependsOn gradle.includedBuilds*.task(":$taskName")
        }
        """
    }

    @Override
    protected String configurationsIfMissingJavaLibrarySupport(boolean hasParent) {
        """
        if (missingJavaLibrarySupport) {
            configurations {
                ${hasParent ? 'api' : ''}
                implementation
                testImplementation
                ${hasParent ? 'compile.extendsFrom api' : ''}
                compile.extendsFrom implementation
                testCompile.extendsFrom testImplementation
            }
        } else if (noJavaLibraryPlugin) {
            configurations {
                ${hasParent ? 'api' : ''}
                ${hasParent ? 'compile.extendsFrom api' : ''}
            }
        }
        """
    }

    @Override
    protected String directDependencyDeclaration(String configuration, String notation) {
        notation.endsWith('()') ? "$configuration $notation" : "$configuration '$notation'"
    }

    @Override
    protected String projectDependencyDeclaration(String configuration, int projectNumber) {
        "$configuration ${dependency(projectNumber)}"
    }
}
