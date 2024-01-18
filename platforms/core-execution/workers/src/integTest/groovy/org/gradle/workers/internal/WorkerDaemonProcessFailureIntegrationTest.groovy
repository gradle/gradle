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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

@Requires(UnitTestPreconditions.Jdk9OrLater)
class WorkerDaemonProcessFailureIntegrationTest extends AbstractDaemonWorkerExecutorIntegrationSpec {
    @Rule
    final BlockingHttpServer blockingHttpServer = new BlockingHttpServer()

    def setup() {
        blockingHttpServer.start()
        executer.withWorkerDaemonsExpirationDisabled()
    }

    void "daemon is gracefully removed if it is killed while idle"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            ${dependsOnPidCapturingAnnotationProcessor()}
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
        file('build/generated/sources/annotationProcessor/java/main/resources/pid.txt').exists()
        def pid1 = file('build/generated/sources/annotationProcessor/java/main/resources/pid.txt').text.strip() as long
        new ProcessFixture(pid1).kill(false)

        when:
        args "--rerun-tasks"
        succeeds("compileJava")

        then:
        file('build/generated/sources/annotationProcessor/java/main/resources/pid.txt').exists()
        def pid2 = file('build/generated/sources/annotationProcessor/java/main/resources/pid.txt').text.strip() as long
        pid2 != pid1
    }

    String dependsOnPidCapturingAnnotationProcessor() {
        settingsFile << """
            include 'processor'
        """
        file('processor/build.gradle') << """
            plugins {
                id 'java'
            }
        """
        file('processor/src/main/java/WorkerPid.java') << """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.SOURCE)
            @Target(ElementType.TYPE)
            public @interface WorkerPid {
            }
        """
        file('processor/src/main/java/WorkerPidProcessor.java') << """
            import javax.annotation.processing.*;
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
        file('processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor') << "WorkerPidProcessor\n"

        return """
            dependencies {
                compileOnly project(':processor')
                annotationProcessor project(':processor')
            }
        """
    }
}
