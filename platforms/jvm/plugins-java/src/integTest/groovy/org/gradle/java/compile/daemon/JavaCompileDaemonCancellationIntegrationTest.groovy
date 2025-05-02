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

package org.gradle.java.compile.daemon

import org.gradle.integtests.fixtures.ProcessFixture
import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

@Requires(UnitTestPreconditions.Jdk9OrLater)
class JavaCompileDaemonCancellationIntegrationTest extends DaemonIntegrationSpec {
    private static final String ANNOTATION_PROCESSOR_PROJECT_NAME = "processor"

    @Rule
    final BlockingHttpServer blockingHttpServer = new BlockingHttpServer()

    private DaemonClientFixture client

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

    def "compiler daemon is stopped when build is cancelled"() {
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
        file('src/main/java/Foo.java') << blockingFooClass
        def handler = blockingHttpServer.expectAndBlock("/block")

        when:
        startBuild("compileJava")

        then:
        handler.waitForAllPendingCalls()

        then:
        cancelBuild()

        then:
        daemons.daemon.becomesIdle()

        and:
        pidFile().exists()
        def pid1 = pidFile().text.strip() as long
        new ProcessFixture(pid1).waitForFinish()

        when:
        handler = blockingHttpServer.expectAndBlock("/block")
        startBuild("compileJava")

        then:
        handler.waitForAllPendingCalls()

        then:
        handler.releaseAll()

        then:
        succeeds()

        and:
        pidFile().exists()
        def pid2 = pidFile().text.strip() as long
        pid2 != pid1
    }

    def "compiler daemon is not stopped when build is cancelled but no compiler daemons are used"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }
            ${dependsOnPidCapturingAnnotationProcessor}
            tasks.withType(JavaCompile).configureEach {
                options.fork = true
            }
            tasks.register("block") {
                doLast {
                    ${blockingHttpServer.callFromTaskAction("block")}
                }
            }
        """
        file('src/main/java/Foo.java') << blockingFooClass
        def handler = blockingHttpServer.expectAndBlock("/block")

        when:
        startBuild("compileJava")

        then:
        handler.waitForAllPendingCalls()

        then:
        handler.releaseAll()

        then:
        succeeds()

        and:
        pidFile().exists()
        def pid1 = pidFile().text.strip() as long
        new ProcessFixture(pid1).waitForFinish()

        when:
        handler = blockingHttpServer.expectAndBlock("/block")
        startBuild("block")

        then:
        handler.waitForAllPendingCalls()

        then:
        cancelBuild()

        then:
        handler.releaseAll()

        then:
        daemons.daemon.becomesIdle()

        when:
        file('src/main/java/Foo.java') << "\n// Comment to trigger recompilation\n"
        handler = blockingHttpServer.expectAndBlock("/block")
        startBuild("compileJava")

        then:
        handler.waitForAllPendingCalls()

        then:
        handler.releaseAll()

        then:
        succeeds()

        and:
        pidFile().exists()
        def pid2 = pidFile().text.strip() as long
        pid2 == pid1
    }

    private void startBuild(String task) {
        executer
            .withArgument('--debug')
            .withWorkerDaemonsExpirationDisabled()
            .withTasks(task)

        client = new DaemonClientFixture(executer.start())
    }

    private void cancelBuild() {
        client.kill()
        assert !client.gradleHandle.standardOutput.contains("BUILD FAIL")
        assert !client.gradleHandle.standardOutput.contains("BUILD SUCCESS")
    }

    private void succeeds() {
        client.gradleHandle.waitForFinish()
        assert client.gradleHandle.standardOutput.contains("BUILD SUCCESS")
        daemons.daemon.becomesIdle()
    }

    TestFile pidFile(String rootPath = null) {
        def root = rootPath ? file(rootPath) : testDirectory
        return root.file('build/generated/sources/annotationProcessor/java/main/resources/pid.txt')
    }

    static String getBlockingFooClass() {
        return """
            @BlockingClass
            public class Foo {
                public static void main(String[] args) {
                    System.out.println("Hello, world!");
                }
            }
        """
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
        file("${ANNOTATION_PROCESSOR_PROJECT_NAME}/src/main/java/BlockingClass.java") << """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.SOURCE)
            @Target(ElementType.TYPE)
            public @interface BlockingClass {
            }
        """
        file("${ANNOTATION_PROCESSOR_PROJECT_NAME}/src/main/java/BlockingProcessor.java") << """
            import javax.annotation.processing.AbstractProcessor;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedAnnotationTypes;
            import javax.annotation.processing.SupportedSourceVersion;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.TypeElement;
            import java.util.Set;
            import java.io.File;
            import javax.tools.StandardLocation;
            import java.io.Writer;
            import javax.tools.FileObject;

            @SupportedAnnotationTypes("BlockingClass")
            @SupportedSourceVersion(SourceVersion.RELEASE_9)
            public class BlockingProcessor extends AbstractProcessor {
                private boolean pidWritten = false;

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

                            ${blockingHttpServer.callFromBuild("block")}
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            }
        """
        file("${ANNOTATION_PROCESSOR_PROJECT_NAME}/src/main/resources/META-INF/services/javax.annotation.processing.Processor") << "BlockingProcessor\n"
    }
}
