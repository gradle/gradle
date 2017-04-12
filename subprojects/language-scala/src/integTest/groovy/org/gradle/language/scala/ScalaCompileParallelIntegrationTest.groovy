/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.scala

import org.gradle.api.internal.tasks.scala.ZincScalaCompilerUtil
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.language.scala.internal.toolchain.DefaultScalaToolProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.GradleVersion
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Issue

@Issue(["GRADLE-3371", "GRADLE-3370"])
@IgnoreIf({GradleContextualExecuter.parallel})
// these tests are always parallel
class ScalaCompileParallelIntegrationTest extends AbstractIntegrationSpec {
    private static final int MAX_PARALLEL_COMPILERS = 4
    def compileTasks = []
    @Rule public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()
    }

    def "multi-project build is multi-process safe"() {
        given:
        def projects = (1..MAX_PARALLEL_COMPILERS)
        projects.each {
            def projectName = "project$it"
            populateProject(projectName)
            settingsFile << """
                include '$projectName'
            """
            compileTasks << ":${projectName}:compileMainJarMainScala".toString()
        }
        buildFile << """
            allprojects {
                $isolatedZincCacheHome
                ${blockUntilAllCompilersAreReady('$path')}
                $userProvidedZincDirSystemProperty
            }
            $lockTimeoutLoggerListenerSource
        """
        expectTasksWithParallelExecuter()

        when:
        succeeds("build")

        then:
        noExceptionThrown()
        zincCacheInterfaceJars.size() == 1
        configuredZincDirInterfaceJars.size() == 0
        output.count(ZincScalaCompilerUtil.ZINC_DIR_IGNORED_MESSAGE) == MAX_PARALLEL_COMPILERS
        leakedTempFiles.isEmpty()

        // Check that we can successfully use an existing compiler-interface.jar as well
        when:
        expectTasksWithParallelExecuter()
        succeeds("clean", "build")

        then:
        noExceptionThrown()
        zincCacheInterfaceJars.size() == 1
        output.count(ZincScalaCompilerUtil.ZINC_DIR_IGNORED_MESSAGE) == MAX_PARALLEL_COMPILERS
        leakedTempFiles.isEmpty()
    }

    // This can be re-enabled once scala compile task uses the worker api
    @Ignore
    def "multiple tasks in a single build are multi-process safe"() {
        given:
        buildFile << scalaBuild

        def components = (1..MAX_PARALLEL_COMPILERS)
        components.each {
            def componentName = "main$it"
            populateComponent(componentName)

            compileTasks << ":compile${componentName.capitalize()}Jar${componentName.capitalize()}Scala".toString()
        }
        buildFile << isolatedZincCacheHome
        buildFile << blockUntilAllCompilersAreReady('$path')
        buildFile << userProvidedZincDirSystemProperty
        buildFile << lockTimeoutLoggerListenerSource
        expectTasksWithParallelExecuter()

        when:
        succeeds("build")

        then:
        noExceptionThrown()
        zincCacheInterfaceJars.size() == 1
        configuredZincDirInterfaceJars.size() == 0
        output.count(ZincScalaCompilerUtil.ZINC_DIR_IGNORED_MESSAGE) == MAX_PARALLEL_COMPILERS
        leakedTempFiles.isEmpty()
    }

    def "multiple independent builds are multi-process safe" () {
        given:
        def projects = (1..MAX_PARALLEL_COMPILERS)
        projects.each {
            def projectName = "project$it"
            settingsFile << """
                include '${projectName}Build'
            """
        }
        buildFile << """
            task buildAll
        """

        projects.each {
            def projectName = "project$it"
            populateProject(projectName)
            projectBuildFile(projectName) << isolatedZincCacheHome
            projectBuildFile(projectName) << blockUntilAllCompilersAreReady(':$project.name:$name')
            projectBuildFile(projectName) << userProvidedZincDirSystemProperty
            projectBuildFile(projectName) << lockTimeoutLoggerListenerSource

            buildFile << """
                project(':${projectName}Build') {
                    ${gradleBuildTask(projectName)}
                }
                buildAll.dependsOn ":${projectName}Build:${projectName}Build"
            """

            compileTasks << ":${projectName}:compileMainJarMainScala".toString()
        }
        expectTasksWithParallelExecuter()

        when:
        succeeds("buildAll")

        then:
        noExceptionThrown()
        zincCacheInterfaceJars.size() == 1
        configuredZincDirInterfaceJars.size() == 0
        output.count(ZincScalaCompilerUtil.ZINC_DIR_IGNORED_MESSAGE) == MAX_PARALLEL_COMPILERS
        leakedTempFiles.isEmpty()
    }

    def "no warning shown when zinc dir is not set by user"() {
        given:
        def projects = (1..4)
        projects.each {
            def projectName = "project$it"
            populateProject(projectName)
            settingsFile << """
                include '$projectName'
            """
            compileTasks << ":${projectName}:compileMainJarMainScala".toString()
        }
        buildFile << """
            allprojects {
                $isolatedZincCacheHome
                ${blockUntilAllCompilersAreReady('$path')}
            }
            $lockTimeoutLoggerListenerSource
        """
        expectTasksWithParallelExecuter()

        when:
        succeeds("build")

        then:
        noExceptionThrown()
        zincCacheInterfaceJars.size() == 1
        !output.contains(ZincScalaCompilerUtil.ZINC_DIR_IGNORED_MESSAGE)
        leakedTempFiles.isEmpty()
    }

    GradleExecuter expectTasksWithParallelExecuter() {
        blockingServer.expectConcurrentExecution(compileTasks, {})
        // Need to set our own GradleUserHomeDir so that we can be sure to cause
        // compiler-interface.jar to be created as part of the compiler instantiation
        // as this is the root of parallelism issues with the Zinc compiler
        return executer.withArgument("--parallel")
                       .withArgument("--max-workers=${MAX_PARALLEL_COMPILERS}")
    }

    def getScalaBuild() {
        return """
            plugins {
                id 'jvm-component'
                id 'scala-lang'
            }
            repositories{
                mavenCentral()
            }

        """
    }

    def getLockTimeoutLoggerListenerSource() {
        return '''
            class LockTimeoutLoggerListener extends TaskExecutionAdapter {
                void afterExecute(Task task, TaskState state) {
                    if(state.failure) {
                        def lockTimeoutException = resolveCausalChain(state.failure).find { it.getClass().name == 'org.gradle.cache.internal.LockTimeoutException' }
                        if (lockTimeoutException?.ownerPid && lockTimeoutException.ownerPid.toString().isNumber()) {
                            def jstackOutput = dumpStacks(lockTimeoutException.ownerPid)
                            if (jstackOutput) {
                                println "jstack for lock owner pid ${lockTimeoutException.ownerPid}"
                                println "-------------------------------------------"
                                println jstackOutput
                                println "-------------------------------------------"
                            }
                        }
                    }
                }

                def resolveCausalChain(throwable) {
                    def causes = []
                    while (throwable != null) {
                        causes.add(throwable);
                        throwable = throwable.getCause();
                    }
                    causes
                }

                def dumpStacks(pid) {
                    try {
                        def jstackExecutable = org.gradle.internal.jvm.Jvm.current().getExecutable("jstack")
                        if (jstackExecutable.isFile()) {
                            return [jstackExecutable.getAbsolutePath(), pid].execute().text
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    null
                }
            }
            gradle.addListener(new LockTimeoutLoggerListener())
        '''
    }

    def jvmComponent(String componentName) {
        return """
            model {
                components {
                    ${componentName}(JvmLibrarySpec)
                }
            }
        """
    }

    def scalaSourceFile(String pkg) {
        return """
            package ${pkg};
            object Main {}
        """
    }

    TestFile getGradleUserHome() {
        return file("gradleUserHome")
    }

    TestFile getConfiguredZincDir() {
        return file("configuredZincDir")
    }

    TestFile getZincCacheHomeDir() {
        return file("zincHome")
    }

    Set<File> getZincCacheInterfaceJars() {
        return findInterfaceJars(zincCacheHomeDir.file("caches/${GradleVersion.current().version}/zinc-${DefaultScalaToolProvider.DEFAULT_ZINC_VERSION}"))
    }

    Set<File> getConfiguredZincDirInterfaceJars() {
        return findInterfaceJars(configuredZincDir)
    }

    Set<File> getLeakedTempFiles() {
        return zincCacheHomeDir.file("tmp").allDescendants().collect { file(it) }
    }

    Set<File> findInterfaceJars(TestFile zincDir) {
        return zincDir.allDescendants()
            .collect { file(it) }
            .findAll { it.name.startsWith("compiler-interface-") && it.name.endsWith(".jar") }
    }

    TestFile projectDir(String projectName) {
        return testDirectory.file(projectName)
    }

    TestFile projectBuildFile(String projectName) {
        return projectDir(projectName).file("build.gradle")
    }

    String getIsolatedZincCacheHome() {
        return """
            tasks.withType(PlatformScalaCompile) {
                options.forkOptions.jvmArgs += "-D${ZincScalaCompilerUtil.ZINC_CACHE_HOME_DIR_SYSTEM_PROPERTY}=${TextUtil.normaliseFileSeparators(zincCacheHomeDir.absolutePath)}"
            }
        """
    }

    String getUserProvidedZincDirSystemProperty() {
        return """
            tasks.withType(PlatformScalaCompile) {
                options.forkOptions.jvmArgs += '-Dzinc.dir=${TextUtil.normaliseFileSeparators(configuredZincDir.absolutePath)}'
            }
        """
    }

    String blockUntilAllCompilersAreReady(String id) {
        return """
            tasks.withType(PlatformScalaCompile) {
                prependParallelSafeAction {
                    logger.lifecycle "$id is waiting for other compile tasks"
                    URL url = new URL("http://localhost:${blockingServer.port}/$id")
                    url.openConnection().getHeaderField('RESPONSE')
                    logger.lifecycle "$id is starting"
                }
            }
        """
    }

    String gradleBuildTask(String projectName) {
        File projectDir = projectDir(projectName)
        return """
                task("${projectName}Build", type: GradleBuild) {
                    startParameter.searchUpwards = false
                    startParameter.projectDir = file("${TextUtil.normaliseFileSeparators(projectDir.absolutePath)}")
                    startParameter.currentDir = file("${TextUtil.normaliseFileSeparators(projectDir.absolutePath)}")
                    startParameter.taskNames = [ "build" ]
                }
            """
    }

    void populateProject(String projectName) {
        def srcDir = projectDir(projectName).file("src/main/scala/org/${projectName}")
        def sourceFile = srcDir.file("Main.scala")
        srcDir.mkdirs()

        projectBuildFile(projectName) << scalaBuild
        projectBuildFile(projectName) << jvmComponent('main')

        sourceFile << scalaSourceFile("org.${projectName}")
    }

    void populateComponent(String componentName) {
        def srcDir = file("src/${componentName}/scala/org/${componentName}")
        def sourceFile = srcDir.file("Main.scala")
        srcDir.mkdirs()

        buildFile << jvmComponent(componentName)
        sourceFile << scalaSourceFile("org.${componentName}")
    }
}
