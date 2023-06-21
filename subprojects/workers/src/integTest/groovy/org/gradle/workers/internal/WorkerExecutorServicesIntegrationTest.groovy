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

package org.gradle.workers.internal

import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.internal.reflect.Instantiator

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

class WorkerExecutorServicesIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    def "workers cannot inject internal services using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.internal.file.FileOperations fileOperations"
        fixture.withWorkActionClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        expect:
        fails("runInWorker")

        and:
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of type FileOperations, or no service of type FileOperations")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "workers can inject FileSystemOperations service using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.extraFields += """
            org.gradle.api.file.FileSystemOperations fileOperations
        """
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.file.FileSystemOperations fileOperations"
        fixture.workActionThatCreatesFiles.constructorAction = "this.fileOperations = fileOperations"
        fixture.workActionThatCreatesFiles.action += """
            fileOperations.copy {
                from "foo"
                into "bar"
            }
            fileOperations.sync {
                from "bar"
                into "baz"
            }
            fileOperations.delete {
                delete "foo"
            }
        """
        fixture.withWorkActionClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """
        file("foo").text = "foo"

        expect:
        succeeds("runInWorker")

        and:
        assertWorkerExecuted("runInWorker")

        and:
        file("bar/foo").text == "foo"
        file("baz/foo").text == "foo"
        file("foo").assertDoesNotExist()

        where:
        isolationMode << ISOLATION_MODES
    }

    def "workers with injected FileSystemOperations service always resolve files from the project directory using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.extraFields += """
            org.gradle.api.file.FileSystemOperations fileOperations
        """
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.file.FileSystemOperations fileOperations"
        fixture.workActionThatCreatesFiles.constructorAction = "this.fileOperations = fileOperations"
        fixture.workActionThatCreatesFiles.action += """
            fileOperations.copy {
                from "foo"
                into "bar"
            }
        """
        fixture.withWorkActionClassInBuildScript()

        settingsFile << """
            include ":anotherProject"
        """

        buildFile << """
            def rootTask = tasks.create("runInWorker", WorkerTask) {
                isolationMode = $isolationMode
            }

            project(":anotherProject") {
                tasks.create("runInWorker2", WorkerTask) {
                    dependsOn rootTask
                    isolationMode = $isolationMode
                }
            }
        """

        file("foo").text = "foo"
        file("anotherProject/foo").text = "foo2"

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        succeeds("runInWorker2")

        then:
        assertWorkerExecuted("runInWorker")
        assertWorkerExecuted("runInWorker2")

        and:
        file("bar/foo").text == "foo"
        file("anotherProject/bar/foo").text == "foo2"

        and:
        if (isolationMode == "'processIsolation'") {
            assertSameDaemonWasUsed("runInWorker", "runInWorker2")
        }

        where:
        isolationMode << ISOLATION_MODES
    }

    def "workers can inject ObjectFactory service using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.extraFields += """
            org.gradle.api.model.ObjectFactory objectFactory

            interface Foo extends Named { }
        """
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.model.ObjectFactory objectFactory"
        fixture.workActionThatCreatesFiles.constructorAction = "this.objectFactory = objectFactory"
        fixture.workActionThatCreatesFiles.action += """
            objectFactory.fileProperty()
            objectFactory.directoryProperty()
            objectFactory.fileCollection()
            objectFactory.fileTree()
            objectFactory.property(String)
            objectFactory.listProperty(String)
            objectFactory.setProperty(String)
            objectFactory.mapProperty(String, String)
            objectFactory.named(Foo, "foo")
            objectFactory.domainObjectSet(Foo)
            objectFactory.domainObjectContainer(Foo)
            objectFactory.polymorphicDomainObjectContainer(Foo)
        """
        fixture.withWorkActionClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """
        file("foo").text = "foo"

        expect:
        succeeds("runInWorker")

        and:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "workers can inject ProviderFactory service using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.extraFields += """
            org.gradle.api.provider.ProviderFactory providerFactory
        """
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.provider.ProviderFactory providerFactory"
        fixture.workActionThatCreatesFiles.constructorAction = "this.providerFactory = providerFactory"
        fixture.workActionThatCreatesFiles.action += """
            assert providerFactory.provider { "foo" }.get() == "foo"
        """
        fixture.withWorkActionClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        expect:
        succeeds("runInWorker")

        and:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "workers can inject ExecOperations service and use exec with #isolationMode isolation"() {
        withTestMainParametersAndServices()

        fixture.workActionThatCreatesFiles.action += """
            execOperations.exec {
                executable org.gradle.internal.jvm.Jvm.current().getJavaExecutable()
                args '-cp', parameters.classpath.asPath, 'org.gradle.TestMain', parameters.projectDir, parameters.testFile
            }
        """
        fixture.withWorkActionClassInBuildScript()

        file('src/main/java/org/gradle/TestMain.java') << testMainSource

        buildFile << """
            apply plugin: "java"

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                def classpath = sourceSets.main.output.classesDirs
                def projectDir = project.projectDir
                def testFile = project.file("\$buildDir/\$name")
                additionalParameters = {
                    it.classpath.from(classpath)
                    it.setProjectDir(projectDir)
                    it.setTestFile(testFile)
                }
                doLast {
                    def classpathFiles = classpath.files
                    assert testFile.exists()
                }
                dependsOn sourceSets.main.output
            }
        """

        expect:
        succeeds("runInWorker")

        and:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "workers can inject ExecOperations service and use javaexec with #isolationMode isolation"() {
        withTestMainParametersAndServices()

        fixture.workActionThatCreatesFiles.action += """
            execOperations.javaexec {
                executable org.gradle.internal.jvm.Jvm.current().getJavaExecutable()
                classpath(parameters.classpath)
                mainClass = 'org.gradle.TestMain'
                args parameters.projectDir, parameters.testFile
            }
        """
        fixture.withWorkActionClassInBuildScript()

        file('src/main/java/org/gradle/TestMain.java') << testMainSource

        buildFile << """
            apply plugin: "java"

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                def classpath = sourceSets.main.output.classesDirs
                def projectDir = project.projectDir
                def testFile = project.file("\$buildDir/\$name")
                additionalParameters = {
                    it.classpath.from(classpath)
                    it.setProjectDir(projectDir)
                    it.setTestFile(testFile)
                }
                doLast {
                    def classpathFiles = classpath.files
                    assert testFile.exists()
                }
                dependsOn sourceSets.main.output
            }
        """

        expect:
        succeeds("runInWorker")

        and:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "workers cannot inject #forbiddenType with #isolationMode"() {
        fixture.workActionThatCreatesFiles.constructorArgs = "${forbiddenType.name} service"
        fixture.workActionThatCreatesFiles.action += """
            throw new RuntimeException()
        """
        fixture.withWorkActionClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        expect:
        fails("runInWorker")

        and:
        failure.assertHasDescription("Execution failed for task ':runInWorker'.")
        failure.assertHasCause("Could not create an instance of type TestWorkAction.")
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of type $forbiddenType.simpleName, or no service of type $forbiddenType.simpleName")

        where:
        [forbiddenType, isolationMode] << [[
            Project, // Not isolated
            ProjectLayout, // Not isolated
            Instantiator, // internal
        ], ISOLATION_MODES].combinations()
    }

    def withTestMainParametersAndServices() {
        fixture.workActionThatCreatesFiles.extraFields += """
            org.gradle.process.ExecOperations execOperations
        """
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.process.ExecOperations execOperations"
        fixture.workActionThatCreatesFiles.constructorAction = "this.execOperations = execOperations"
        fixture.testParameterType.fields += [
            classpath: "ConfigurableFileCollection",
            projectDir: "File",
            testFile: "File"
        ]
    }

    def getTestMainSource() {
        return """
            package org.gradle;

            import java.io.File;

            public class TestMain {
                public static void main(String[] args) throws Exception {
                    File expectedWorkingDir = new File(args[0]).getCanonicalFile();
                    File actualWorkingDir = new File(System.getProperty("user.dir")).getCanonicalFile();
                    if (!expectedWorkingDir.getCanonicalFile().equals(actualWorkingDir)) {
                        throw new RuntimeException(String.format("Unexpected working directory '%s', expected '%s'.", actualWorkingDir, expectedWorkingDir));
                    }
                    File file = new File(args[1]);
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
            }
        """
    }
}
