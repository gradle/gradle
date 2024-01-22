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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.ProcessFixture
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

@Requires(UnitTestPreconditions.Jdk9OrLater)
class WorkerDaemonProcessFailureIntegrationTest extends AbstractDaemonWorkerExecutorIntegrationSpec {
    private static final String ANNOTATION_PROCESSOR_PROJECT_NAME = "processor"

    @Rule
    final BlockingHttpServer blockingHttpServer = new BlockingHttpServer()

    def setup() {
        blockingHttpServer.start()
        if (OperatingSystem.current().windows) {
            // The killed worker on Windows will cause a broken pipe error to be printed to the console,
            // so we disable stack trace checks to avoid the test failing.
            executer.withStackTraceChecksDisabled()
        }

        settingsFile << """
            include '${ANNOTATION_PROCESSOR_PROJECT_NAME}'
        """
        writeAnnotationProcessorProject()
    }

    void "daemon is gracefully removed if it is killed while idle in between builds"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            ${dependsOnPidCapturingAnnotationProcessor}

            tasks.withType(JavaCompile).configureEach {
                options.fork = true
            }
        """
        file('src/main/java/Foo.java') << """
            @WorkerPid
            public class Foo {
                public static void main(String[] args) {
                    System.out.println("Hello world!");
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        pidFile().exists()
        def pid1 = pidFile().text.strip() as long
        new ProcessFixture(pid1).kill(false)

        when:
        args "--rerun-tasks"
        succeeds("compileJava")

        then:
        pidFile().exists()
        def pid2 = pidFile().text.strip() as long
        pid2 != pid1

        and:
        outputContainsKilledWorkerWarning()
    }

    void "daemon is gracefully removed if it is killed while idle in between calls"() {
        given:
        settingsFile << """
            include 'other'
        """
        buildFile << """
            plugins {
                id 'java'
            }

            ${dependsOnPidCapturingAnnotationProcessor}
            dependencies {
                implementation project(':other')
            }

            tasks.withType(JavaCompile).configureEach {
                options.fork = true
                doFirst {
                    ${blockingHttpServer.callFromTaskAction('between')}
                }
            }
        """
        file('src/main/java/Foo.java') << """
            @WorkerPid
            public class Foo {
                public static void main(String[] args) {
                    System.out.println("Hello world!");
                }
            }
        """
        file ('other/build.gradle') << """
            plugins {
                id 'java'
            }

            ${dependsOnPidCapturingAnnotationProcessor}

            tasks.withType(JavaCompile).configureEach {
                options.fork = true
            }
        """
        file('other/src/main/java/Bar.java') << """
            @WorkerPid
            public class Bar {
                public static void main(String[] args) {
                    System.out.println("Hello world!");
                }
            }
        """
        def handler = blockingHttpServer.expectAndBlock("between")

        when:
        def gradle = executer.withTasks("compileJava").start()

        then:
        handler.waitForAllPendingCalls()

        and:
        pidFile('other').exists()
        def pid1 = pidFile('other').text.strip() as long
        new ProcessFixture(pid1).kill(false)

        when:
        handler.releaseAll()
        result = gradle.waitForFinish()

        then:
        pidFile().exists()
        def pid2 = pidFile().text.strip() as long
        pid2 != pid1

        and:
        outputContainsKilledWorkerWarning()
    }

    void "daemon is gracefully removed if it is killed while idle before clients are stopped"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            ${dependsOnPidCapturingAnnotationProcessor}

            tasks.withType(JavaCompile).configureEach {
                options.fork = true
                doLast {
                    ${blockingHttpServer.callFromTaskAction('after')}
                    services.get(org.gradle.workers.internal.WorkerDaemonFactory.class)
                        .clientsManager.selectIdleClientsToStop { clients -> clients }
                }
            }
        """
        file('src/main/java/Foo.java') << """
            @WorkerPid
            public class Foo {
                public static void main(String[] args) {
                    System.out.println("Hello world!");
                }
            }
        """
        def handler = blockingHttpServer.expectAndBlock("after")

        when:
        def gradle = executer.withTasks("compileJava").start()

        then:
        handler.waitForAllPendingCalls()

        and:
        pidFile().exists()
        def pid1 = pidFile().text.strip() as long
        kill(pid1)

        when:
        handler.releaseAll()
        result = gradle.waitForFinish()

        then:
        outputContainsKilledWorkerWarning()
    }

    static void kill(long pid1) {
        new ProcessFixture(pid1).kill(false)
    }

    void outputContainsKilledWorkerWarning() {
        if (OperatingSystem.current().windows) {
            outputContains(" exited unexpectedly with exit code 1.")
        } else {
            outputContains(" exited unexpectedly after being killed with signal 9.  This is likely because an external process has killed the worker.")
        }
    }

    TestFile pidFile(String rootPath = null) {
        def root = rootPath ? file(rootPath) : testDirectory
        return root.file('build/generated/sources/annotationProcessor/java/main/resources/pid.txt')
    }

    static String getDependsOnPidCapturingAnnotationProcessor() {
        return """
            dependencies {
                compileOnly project(':${ANNOTATION_PROCESSOR_PROJECT_NAME}')
                annotationProcessor project(':${ANNOTATION_PROCESSOR_PROJECT_NAME}')
            }
        """
    }

    private void writeAnnotationProcessorProject() {
        file("${ANNOTATION_PROCESSOR_PROJECT_NAME}/build.gradle") << """
            plugins {
                id 'java'
            }
        """
        file("${ANNOTATION_PROCESSOR_PROJECT_NAME}/src/main/java/WorkerPid.java") << """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.SOURCE)
            @Target(ElementType.TYPE)
            public @interface WorkerPid {
            }
        """
        file("${ANNOTATION_PROCESSOR_PROJECT_NAME}/src/main/java/WorkerPidProcessor.java") << """
            import javax.annotation.processing.AbstractProcessor;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedAnnotationTypes;
            import javax.annotation.processing.SupportedSourceVersion;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.TypeElement;
            import java.util.Set;
            import javax.tools.FileObject;
            import javax.tools.StandardLocation;
            import java.io.Writer;

            @SupportedAnnotationTypes("WorkerPid")
            @SupportedSourceVersion(SourceVersion.RELEASE_9)
            public class WorkerPidProcessor extends AbstractProcessor {
                private boolean pidWritten;

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    try {
                        if (!pidWritten) {
                            System.out.println("Worker daemon pid is " + ProcessHandle.current().pid());
                            FileObject file = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "resources", "pid.txt");
                            Writer writer = file.openWriter();
                            writer.write(String.valueOf(ProcessHandle.current().pid()));
                            writer.close();
                            pidWritten = true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            }
        """
        file("${ANNOTATION_PROCESSOR_PROJECT_NAME}/src/main/resources/META-INF/services/javax.annotation.processing.Processor") << "WorkerPidProcessor\n"
    }
}
