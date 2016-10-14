/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance

import org.gradle.internal.jvm.Jvm
import org.gradle.performance.categories.Experiment
import org.gradle.performance.categories.ToolingApiPerformanceTest
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([ToolingApiPerformanceTest, Experiment])
class ToolingApiIdeModelCrossVersionPerformanceTest extends AbstractToolingApiCrossVersionPerformanceTest {

    @Unroll
    def "building Eclipse model for a #template project"() {
        given:

        experiment(template, "get $template EclipseProject model") {
            warmUpCount = 20
            invocationCount = 30
            sleepAfterTestRoundMillis = 25
            // rebaselined because of https://github.com/gradle/performance/issues/99
            targetVersions = ['3.2-20161010071950+0000']
            action {
                def model = model(tapiClass(EclipseProject))
                    .setJvmArguments(
                    '-Xms1g', '-Xmx1g',
                    '-Dorg.gradle.performance.measurement.disabled=true'
                ).get()
                // we must actually do something to highlight some performance issues
                forEachEclipseProject(model) {
                    buildCommands.each {
                        it.name
                        it.arguments
                    }
                    withGradleProject(gradleProject)
                    classpath.collect {
                        [it.exported, it.file, it.gradleModuleVersion.group, it.gradleModuleVersion.name, it.gradleModuleVersion.version, it.javadoc, it.source]
                    }
                    javaSourceSettings?.jdk?.javaHome
                    withJava(javaSourceSettings?.jdk?.javaVersion)
                    withJava(javaSourceSettings?.sourceLanguageLevel)
                    withJava(javaSourceSettings?.targetBytecodeVersion)
                    projectNatures.each {
                        it.id
                    }
                    projectDependencies.each {
                        it.exported
                        it.path
                    }
                    description
                    name
                    linkedResources.each {
                        it.name
                        it.location
                        it.locationUri
                        it.type
                    }
                    projectDirectory
                    sourceDirectories.each {
                        it.path
                        it.directory
                    }
                }
            }
        }

        when:
        def results = performMeasurements()

        then:
        results.assertCurrentVersionHasNotRegressed()

        where:
        template << ["smallOldJava", "mediumOldJava", "bigOldJava", "lotDependencies"]
    }

    private static void sendCommand(String command, int port) {
        def socket = new Socket("127.0.0.1", port)
        try {
            socket.outputStream.withStream { output ->
                output.write("${command}\r\n".bytes)
            }
        } finally {
            socket.close()
        }
    }

    private static void sendYJPCommand(String command, int port) {
        [Jvm.current().javaExecutable, '-jar', '/home/cchampeau/TOOLS/yjp-2016.02/lib/yjp-controller-api-redist.jar', '127.0.0.1', "$port", command].execute()
    }

    @Unroll
    def "building IDEA model for a #template project"() {
        given:
        int port = 10000
        experiment(template, "get $template IdeaProject model") {
            /*
            // uncomment to enable profiling with Honest Profiler, Yourkit, debugging, ...
            listener = new BuildExperimentListenerAdapter() {
                @Override
                void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                    if (invocationInfo.iterationNumber==1 && invocationInfo.phase == BuildExperimentRunner.Phase.MEASUREMENT) {
                        println "Starting sampling..."
                        //sendCommand('start', port)
                        sendYJPCommand('start-cpu-call-counting', port)
                        sendYJPCommand('enable-stack-telemetry', port)
                    }
                }

                @Override
                void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                    if (invocationInfo.iterationNumber==invocationInfo.iterationMax-1 && invocationInfo.phase == BuildExperimentRunner.Phase.MEASUREMENT) {
                        sendYJPCommand('capture-performance-snapshot', port)
                        sendYJPCommand('stop-cpu-call-counting', port)
                        sendYJPCommand('disable-stack-telemetry', port)
                        port++
                    }
                }
            }*/
            warmUpCount = 20
            invocationCount = 30
            sleepAfterTestRoundMillis = 25
            targetVersions = targetGradleVersions
            action {
                def version = tapiClass(GradleVersion).current().version
                def model = model(tapiClass(IdeaProject))
                    .setJvmArguments(
                    //'-XX:+UnlockDiagnosticVMOptions', '-XX:+DebugNonSafepoints',
                    '-Xms1g', '-Xmx1g',
                    '-Dorg.gradle.performance.measurement.disabled=true'
                    //"-agentpath:/home/cchampeau/TOOLS/yjp-2016.02/bin/linux-x86-64/libyjpagent.so=port=$port",
                    // "-agentpath:/home/cchampeau/TOOLS/honest-profiler/liblagent.so=interval=7,logPath=/tmp/fg/honestprofiler_${version}.hpl,port=${port},host=127.0.0.1,start=0",
                ).get()
                // we must actually do something to highlight some performance issues
                model.with {
                    name
                    description
                    jdkName
                    languageLevel.level
                    withJava(javaLanguageSettings.languageLevel)
                    withJava(javaLanguageSettings.targetBytecodeVersion)
                    withJava(javaLanguageSettings.jdk.javaVersion)
                    javaLanguageSettings.jdk.javaHome
                    modules.each {
                        it.compilerOutput.inheritOutputDirs
                        it.compilerOutput.outputDir
                        it.compilerOutput.testOutputDir
                        it.contentRoots.each {
                            it.excludeDirectories
                            withIdeaSources(it.generatedSourceDirectories)
                            withIdeaSources(it.generatedTestDirectories)
                            withIdeaSources(it.sourceDirectories)
                            withIdeaSources(it.testDirectories)
                        }
                        it.dependencies.each {
                            it.scope.scope
                            if (tapiClass(ExternalDependency).isAssignableFrom(it.class)) {
                                it.gradleModuleVersion.group
                                it.gradleModuleVersion.name
                                it.gradleModuleVersion.version
                            }
                        }
                        withGradleProject(it.gradleProject)
                    }
                }
            }
        }

        when:
        def results = performMeasurements()

        then:
        results.assertCurrentVersionHasNotRegressed()

        where:
        // rebaselined because of https://github.com/gra
        template          | targetGradleVersions
        "smallOldJava"    | ['3.2-20161010071950+0000']
        "mediumOldJava"   | ['3.2-20161010071950+0000']
        "bigOldJava"      | ['3.2-20161010071950+0000']
        "lotDependencies" | ['3.2-20161010071950+0000']
    }

    private static void forEachEclipseProject(def elm, @DelegatesTo(value=EclipseProject) Closure<?> action) {
        action.delegate = elm
        action.call()
        elm.children?.each {
            forEachEclipseProject(it, action)
        }
    }

    private static void withIdeaSources(def sources) {
        sources.each {
            it.generated
            it.directory
        }
    }

    private static void withGradleProject(def gradleProject) {
        gradleProject.buildDirectory
        gradleProject.path
        gradleProject.buildScript.sourceFile
        gradleProject.buildDirectory
        gradleProject.name
        gradleProject.projectDirectory
        gradleProject.description
        gradleProject.tasks.collect {
            it.name
            it.project
            it.path
            it.description
            it.displayName
            it.group
            it.public
        }
    }

    private static void withJava(def it) {
        if (it != null) {
            it.java5
            it.java5Compatible
            it.java6
            it.java6Compatible
            it.java7
            it.java7Compatible
            it.java8Compatible
            it.java9Compatible
        }
    }
}
