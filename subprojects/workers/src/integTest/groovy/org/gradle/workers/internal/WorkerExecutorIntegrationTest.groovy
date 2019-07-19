/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.workers.IsolationMode
import org.gradle.workers.fixtures.WorkerExecutorFixture
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

@IntegrationTestTimeout(120)
@Unroll
class WorkerExecutorIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "can create and use a worker execution defined in buildSrc in #isolationMode"() {
        fixture.withWorkerExecutionClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertWorkerExecuted("runInWorker")

        when:
        file('buildSrc/src/main/java/AnotherFoo.java') << """
            class AnotherFoo extends org.gradle.other.Foo {
            }
        """
        buildFile << """
            runInWorker {
                foo = new AnotherFoo()
            }
        """
        succeeds("runInWorker")

        then:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can create and use a worker execution defined in build script in #isolationMode"() {
        fixture.withWorkerExecutionClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertWorkerExecuted("runInWorker")

        when:
        file('buildSrc/src/main/java/AnotherFoo.java') << """
            class AnotherFoo extends org.gradle.other.Foo {
            }
        """
        buildFile << """
            runInWorker {
                foo = new AnotherFoo()
            }
        """
        succeeds("runInWorker")

        then:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can create and use a worker execution defined in an external jar in #isolationMode"() {
        def workerExecutionJarName = "workerExecution.jar"
        withWorkerExecutionClassInExternalJar(file(workerExecutionJarName))

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("$workerExecutionJarName")
                }
            }

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertWorkerExecuted("runInWorker")

        when:
        buildFile << """
            runInWorker {
                foo = new AnotherFoo()
            }
        """
        succeeds("runInWorker")

        then:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "re-uses an existing idle worker daemon"() {
        executer.withWorkerDaemonsExpirationDisabled()
        fixture.withWorkerExecutionClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
            }

            task reuseDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    def "starts a new worker daemon when existing worker daemons are incompatible"() {
        fixture.withWorkerExecutionClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask)

            task startNewDaemon(type: WorkerTask) {
                dependsOn runInDaemon
                isolationMode = IsolationMode.PROCESS

                // Force a new daemon to be used
                additionalForkOptions = {
                    it.systemProperty("foo", "bar")
                }
            }
        """

        when:
        succeeds("startNewDaemon")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "starts a new worker daemon when there are no idle compatible worker daemons available"() {
        blockingServer.start()
        blockingServer.expectConcurrent("runInDaemon", "startNewDaemon")

        fixture.withWorkerExecutionClassInBuildSrc()
        fixture.withBlockingWorkerExecutionClassInBuildSrc("http://localhost:${blockingServer.port}")

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                workerExecutionClass = BlockingWorkerExecution.class
            }

            task startNewDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                workerExecutionClass = BlockingWorkerExecution.class
            }

            task runAllDaemons {
                dependsOn runInDaemon, startNewDaemon
            }
        """

        when:
        args("--parallel")
        succeeds("runAllDaemons")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "re-uses an existing compatible worker daemon when a different worker execution is executed"() {
        executer.withWorkerDaemonsExpirationDisabled()
        fixture.withAlternateWorkerExecutionClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
            }

            task reuseDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                workerExecutionClass = AlternateWorkerExecution.class
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    def "throws if worker used from a thread with no current build operation in #isolationMode"() {
        given:
        fixture.withWorkerExecutionClassInBuildSrc()

        and:
        buildFile << """
            class WorkerTaskUsingCustomThreads extends WorkerTask {
                @TaskAction
                void executeTask() {
                    def thrown = null
                    def customThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                workerExecutor.execute(workerExecutionClass) { config ->
                                    config.isolationMode = $isolationMode
                                    if (isolationMode == IsolationMode.PROCESS) {
                                        forkOptions.maxHeapSize = "64m"
                                    }
                                    config.forkOptions(additionalForkOptions)
                                    config.classpath.from(additionalClasspath)
                                    config.parameters {
                                        files = list.collect { it as String }
                                        outputDir = new File(outputFileDirPath)
                                        foo = owner.foo
                                    }
                                }.get()
                            } catch(Exception ex) {
                                thrown = ex
                            }
                        }
                    })
                    customThread.start()
                    customThread.join()
                    if(thrown) {
                        throw thrown
                    }
                }
            }

            task runInWorker(type: WorkerTaskUsingCustomThreads)
        """.stripIndent()

        when:
        fails 'runInWorker'

        then:
        failure.assertHasCause 'An attempt was made to submit work from a thread not managed by Gradle.  Work may only be submitted from a Gradle-managed thread.'

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can set a custom display name for work items in #isolationMode"() {
        given:
        fixture.withWorkerExecutionClassInBuildSrc()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                displayName = "Test Work"
            }
        """

        when:
        succeeds("runInWorker")

        then:
        def operation = buildOperations.only(ExecuteWorkItemBuildOperationType)
        operation.displayName == "Test Work"
        with (operation.details) {
            className == "org.gradle.test.TestWorkerExecution"
            displayName == "Test Work"
        }

        where:
        isolationMode << ISOLATION_MODES
    }

    def "includes failures in build operation in #isolationMode"() {
        given:
        fixture.withWorkerExecutionClassInBuildSrc()
        fixture.workerExecutionThatFails.writeToBuildFile()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                workerExecutionClass = WorkerExecutionThatFails.class
            }
        """

        when:
        fails("runInWorker")

        then:
        def operation = buildOperations.only(ExecuteWorkItemBuildOperationType)
        operation.displayName == "WorkerExecutionThatFails"
        operation.failure == "java.lang.RuntimeException: Failure from worker execution"

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can use a parameter that references classes in other packages in #isolationMode"() {
        fixture.withWorkerExecutionClassInBuildSrc()
        withParameterClassReferencingClassInAnotherPackage()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        expect:
        succeeds("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "classloader is not isolated when using IsolationMode.NONE"() {
        fixture.withWorkerExecutionClassInBuildScript()

        buildFile << """
            class MutableItem {
                static String value = "foo"
            }
            
            abstract class MutatingWorkerExecution extends TestWorkerExecution {
                @Inject
                public MutatingWorkerExecution() { }
                
                public void execute() {
                    MutableItem.value = getParameters().files[0]
                }
            }
            
            task mutateValue(type: WorkerTask) {
                list = [ "bar" ]
                isolationMode = IsolationMode.NONE
                workerExecutionClass = MutatingWorkerExecution.class
            } 
            
            task verifyNotIsolated {
                dependsOn mutateValue
                doLast {
                    assert MutableItem.value == "bar"
                }
            }
        """

        expect:
        succeeds "verifyNotIsolated"
    }

    def "user classes are isolated when using IsolationMode.CLASSLOADER"() {
        fixture.withWorkerExecutionClassInBuildScript()

        buildFile << """
            class MutableItem {
                static String value = "foo"
            }
            
            abstract class MutatingWorkerExecution extends TestWorkerExecution {
                @Inject
                public MutatingWorkerExecution() { }
                
                public void execute() {
                    MutableItem.value = getParameters().files[0]
                }
            }
            
            task mutateValue(type: WorkerTask) {
                list = [ "bar" ]
                isolationMode = IsolationMode.CLASSLOADER
                workerExecutionClass = MutatingWorkerExecution.class
            } 
            
            task verifyIsolated {
                dependsOn mutateValue
                doLast {
                    assert MutableItem.value == "foo"
                }
            }
        """

        expect:
        succeeds "verifyIsolated"
    }

    def "user classpath is isolated when using #isolationMode"() {
        fixture.withWorkerExecutionClassInBuildScript()

        buildFile << """
            import java.util.jar.Manifest 
            
            repositories {
                mavenCentral()
            }
            
            configurations {
                customGuava
            }
            
            dependencies {
                customGuava "com.google.guava:guava:23.1-jre"
            }
            
            abstract class GuavaVersionWorkerExecution extends TestWorkerExecution {
                @Inject
                public GuavaVersionWorkerExecution() { }
                
                public void execute() {
                    Enumeration<URL> resources = this.getClass().getClassLoader()
                            .getResources("META-INF/MANIFEST.MF")
                    while (resources.hasMoreElements()) {
                        InputStream inputStream = resources.nextElement().openStream()
                        Manifest manifest = new Manifest(inputStream)
                        java.util.jar.Attributes mainAttributes = manifest.getMainAttributes()
                        String symbolicName = mainAttributes.getValue("Bundle-SymbolicName")
                        if ("com.google.guava".equals(symbolicName)) {
                            println "Guava version: " + mainAttributes.getValue("Bundle-Version")
                            break
                        }
                    }
                    
                    // This method was removed in Guava 24.0
                    def predicatesClass = this.getClass().getClassLoader().loadClass("com.google.common.base.Predicates")
                    assert predicatesClass.getDeclaredMethods().any { it.name == "assignableFrom" }
                }
            }
            
            task checkGuavaVersion(type: WorkerTask) {
                isolationMode = IsolationMode.${isolationMode}
                workerExecutionClass = GuavaVersionWorkerExecution.class
                additionalClasspath = configurations.customGuava
            } 
        """

        expect:
        succeeds "checkGuavaVersion"

        and:
        outputContains("Guava version: 23.1.0.jre")

        where:
        isolationMode << [IsolationMode.CLASSLOADER, IsolationMode.PROCESS]
    }

    def "classloader is minimal when using #isolationMode"() {
        fixture.withWorkerExecutionClassInBuildSrc()

        buildFile << """         
            abstract class SneakyWorkerExecution extends TestWorkerExecution {            
                @Inject
                public SneakyWorkerExecution() { }
                
                public void execute() {
                    super.execute()
                    // These classes were chosen to be relatively stable and would be unusual to see in a worker. 
                    def gradleApiClasses = [
                        "${com.google.common.collect.Lists.canonicalName}",
                    ]
                    def reachableClasses = gradleApiClasses.findAll { reachable(it) }
                    if (!reachableClasses.empty) {
                        throw new IllegalArgumentException("These classes should not be visible to the worker action: " + reachableClasses)
                    }
                }
                
                boolean reachable(String classname) {
                    try {
                        Class.forName(classname)
                        // bad! the class was leaked into the worker classpath
                        return true
                    } catch (Exception e) {
                        // The class was not found in the classpath
                        return false
                    }
                }
            }
            
            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.$isolationMode
                workerExecutionClass = SneakyWorkerExecution
            } 
        """

        when:
        succeeds("runInWorker", "-i")
        then:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << [IsolationMode.CLASSLOADER, IsolationMode.PROCESS]
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    @Issue("https://github.com/gradle/gradle/issues/8628")
    def "can find resources in the classpath via the context classloader using #isolationMode"() {
        fixture.withWorkerExecutionClassInBuildSrc()

        file('foo.txt').text = "foo!"
        buildFile << """
            apply plugin: "base"

            abstract class ResourceWorkerExecution extends TestWorkerExecution {
                @Inject
                public ResourceWorkerExecution() { }

                public void execute() {
                    super.execute()
                    def resource = Thread.currentThread().getContextClassLoader().getResource("foo.txt")
                    assert resource != null && resource.getPath().endsWith('build/libs/foo.jar!/foo.txt')
                    println resource
                }
            }

            task jarFoo(type: Jar) {
                archiveBaseName = 'foo'
                from 'foo.txt'
            }

            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.${isolationMode}
                workerExecutionClass = ResourceWorkerExecution
                additionalClasspath = tasks.jarFoo.outputs.files
                dependsOn jarFoo
            } 
        """

        when:
        succeeds("runInWorker")

        then:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << [IsolationMode.CLASSLOADER, IsolationMode.PROCESS]
    }

    def "workers that change the context classloader don't affect future work in #isolationMode"() {
        fixture.withWorkerExecutionClassInBuildScript()

        WorkerExecutorFixture.ExecutionClass workerThatChangesContextClassLoader = fixture.getWorkerExecutionThatCreatesFiles("ClassLoaderChangingWorker")
        workerThatChangesContextClassLoader.with {
            action += """
                URL[] urls = parameters.files.collect { new File(getParameters().getOutputDir(), it).toURI().toURL() }
                ClassLoader classloader = new URLClassLoader(urls)
                Thread.currentThread().setContextClassLoader(classloader)
                println "Thread id: " + Thread.currentThread().id
            """
        }
        workerThatChangesContextClassLoader.writeToBuildFile()

        WorkerExecutorFixture.ExecutionClass workerThatChecksClassLoader = fixture.getWorkerExecutionThatCreatesFiles("ClassLoaderVerifyingWorker")
        workerThatChecksClassLoader.with {
            action += """
                File outputDir = new File(getParameters().getOutputDir().absolutePath.replace("checkClassLoader", "changeClassloader"))
                URL[] urls = parameters.files.collect { new File(outputDir, it).toURI().toURL() }
                assert !urls.any { Thread.currentThread().getContextClassLoader().URLs.contains(it) }
                println "Thread id: " + Thread.currentThread().id
            """
        }
        workerThatChecksClassLoader.writeToBuildFile()

        buildFile << """
            task changeClassloader(type: WorkerTask) {
                isolationMode = $isolationMode
                workerExecutionClass = ${workerThatChangesContextClassLoader.name}.class
            }
            
            task checkClassLoader(type: WorkerTask) {
                dependsOn changeClassloader
                isolationMode = $isolationMode
                workerExecutionClass = ${workerThatChecksClassLoader.name}.class
            }
        """

        expect:
        succeeds "checkClassLoader"

        and:
        assertWorkerExecuted("changeClassloader")
        assertWorkerExecuted("checkClassLoader")

        where:
        isolationMode << ISOLATION_MODES
    }

    void withParameterClassReferencingClassInAnotherPackage() {
        file("buildSrc/src/main/java/org/gradle/another/Bar.java").text = """
            package org.gradle.another;
            
            import java.io.Serializable;
            
            public class Bar implements Serializable { }
        """

        file("buildSrc/src/main/java/org/gradle/other/Foo.java").text = """
            package org.gradle.other;

            import java.io.Serializable;
            import org.gradle.another.Bar;

            public class Foo implements Serializable { 
                Bar bar = new Bar();
            }
        """
    }

    void withWorkerExecutionClassInExternalJar(File workerExecutionJar) {
        file("buildSrc").deleteDir()

        def builder = artifactBuilder()
        fixture.workerExecutionThatCreatesFiles.writeToFile builder.sourceFile("org/gradle/test/TestWorkerExecution.java")
        fixture.testParameterType.writeToFile builder.sourceFile("org/gradle/test/TestParameters.java")

        builder.sourceFile("org/gradle/other/Foo.java") << """
            $fixture.parameterClass
        """
        builder.sourceFile('AnotherFoo.java') << """
            class AnotherFoo extends org.gradle.other.Foo { }
        """
        builder.sourceFile("org/gradle/test/FileHelper.java") << """
            $fixture.fileHelperClass
        """
        builder.buildJar(workerExecutionJar)

        fixture.addImportToBuildScript("org.gradle.test.TestWorkerExecution")
    }
}
