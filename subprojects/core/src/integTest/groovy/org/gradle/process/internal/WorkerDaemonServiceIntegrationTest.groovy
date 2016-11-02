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
import spock.lang.Ignore


class WorkerDaemonServiceIntegrationTest extends AbstractIntegrationSpec {

    def "can create and use a daemon runnable defined in buildSrc"() {
        def outputFileDir = file("build/worker")
        def outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)
        def list = [ 1, 2, 3 ]

        file("buildSrc/src/main/java/org/gradle/test/MyRunnable.java") << """
            package org.gradle.test;

            import org.gradle.api.Action;
            import java.io.Serializable;
            import java.io.File;
            import java.util.List;

            public class MyRunnable implements Runnable {
                private final List<String> files;
                private File outputDir;

                public MyRunnable(List<String> files, File outputDir) {
                    this.files = files;
                    this.outputDir = outputDir;
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
        buildFile << """
            import org.gradle.test.MyRunnable

            class MyTask extends DefaultTask {
                def list = []

                @TaskAction
                void executeTask() {
                    project.workerDaemons.daemonRunnable()
                        .forkOptions {
                            it.workingDir(project.projectDir)
                        }
                        .implementationClass(MyRunnable.class)
                        .params(list.collect { it as String }, new File("${outputFileDirPath}"))
                        .execute()
                }
            }

            task runActions(type: MyTask) {
                list = $list
            }
        """

        when:
        succeeds("runActions")

        then:
        list.each {
            outputFileDir.file(it).assertExists()
        }
    }

    def "can create and use a daemon runnable defined in build script"() {
        def outputFileDir = file("build/worker")
        def outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)
        def list = [ 1, 2, 3 ]

        file("buildSrc/src/main/groovy/Foo.groovy") << """
            class Foo implements Serializable {
            }
        """

        buildFile << """
            class MyRunnable implements Runnable {
                private final List<String> files;
                private File outputDir;
                private Foo foo;

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

            class MyTask extends DefaultTask {
                def list = []

                @TaskAction
                void executeTask() {
                    project.workerDaemons.daemonRunnable()
                        .forkOptions {
                            it.workingDir(project.projectDir)
                        }
                        .implementationClass(MyRunnable.class)
                        .params(list.collect { it as String }, new File("${outputFileDirPath}"), new Foo())
                        .execute()
                }
            }

            task runActions(type: MyTask) {
                list = $list
            }
        """

        when:
        succeeds("runActions")

        then:
        list.each {
            outputFileDir.file(it).assertExists()
        }
    }
}
