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

package org.gradle.process.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil


class WorkerDaemonServiceIntegrationTest extends AbstractIntegrationSpec {
    def outputFileDir = file("build/worker")
    def outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)
    def list = [ 1, 2, 3 ]

    def "can create and use a daemon runnable defined in buildSrc"() {
        withBuildSrcParameterClass()
        withBuildSrcRunnableClass()

        buildFile << """
            import org.gradle.test.MyRunnable

            $taskUsingWorkerDaemon
        """

        when:
        succeeds("runActions")

        then:
        list.each {
            outputFileDir.file(it).assertExists()
        }
    }

    def "can create and use a daemon runnable defined in build script"() {
        withBuildSrcParameterClass()

        buildFile << """
            $runnableThatCreatesFiles

            $taskUsingWorkerDaemon
        """

        when:
        succeeds("runActions")

        then:
        list.each {
            outputFileDir.file(it).assertExists()
        }
    }

    String getTaskUsingWorkerDaemon() {
        return """
            import javax.inject.Inject
            import org.gradle.process.daemon.WorkerDaemonService
            import org.gradle.other.Foo

            class MyTask extends DefaultTask {
                def list = []

                @Inject
                WorkerDaemonService getWorkerDaemons() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void executeTask() {
                    workerDaemons.daemonRunnable(MyRunnable.class)
                        .forkOptions {
                            it.workingDir(project.projectDir)
                        }
                        .params(list.collect { it as String }, new File("${outputFileDirPath}"), new Foo())
                        .execute()
                }
            }

            task runActions(type: MyTask) {
                list = $list
            }
        """
    }

    String getRunnableThatCreatesFiles() {
        return """
            import java.io.Serializable;
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;

            public class MyRunnable implements Runnable {
                private final List<String> files;
                private final File outputDir;
                private final Foo foo ;

                public MyRunnable(List<String> files, File outputDir, Foo foo) {
                    this.files = files;
                    this.outputDir = outputDir;
                    this.foo = foo;
                }

                public void run() {
                    outputDir.mkdirs();

                    for (String name : files) {
                        try {
                            File outputFile = new File(outputDir, name);
                            outputFile.createNewFile();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        """
    }

    void withBuildSrcParameterClass() {
        file("buildSrc/src/main/java/org/gradle/other/Foo.java") << """
            package org.gradle.other;

            import java.io.Serializable;

            public class Foo implements Serializable { }
        """
    }

    void withBuildSrcRunnableClass() {
        file("buildSrc/src/main/java/org/gradle/test/MyRunnable.java") << """
            package org.gradle.test;

            $runnableThatCreatesFiles
        """
    }
}
