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

package org.gradle.workers.fixtures

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TextUtil
import org.gradle.workers.IsolationMode

class WorkerExecutorFixture {
    public static final ISOLATION_MODES = (IsolationMode.values() - IsolationMode.AUTO).collect { "IsolationMode.${it.toString()}" }
    def outputFileDir
    def outputFileDirPath
    def list = [ 1, 2, 3 ]
    private final TestNameTestDirectoryProvider temporaryFolder

    WorkerExecutorFixture(TestNameTestDirectoryProvider temporaryFolder) {
        this.temporaryFolder = temporaryFolder
        this.outputFileDir = temporaryFolder.file("build/workers")
        outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)
    }

    def prepareTaskTypeUsingWorker() {
        buildFile << """
            import org.gradle.workers.*
            $taskTypeUsingWorker
        """
    }

    String getTaskTypeUsingWorker() {
        withParameterClassInBuildSrc()
        withFileHelperClassInBuildSrc()

        return """
            import javax.inject.Inject
            import org.gradle.other.Foo

            class WorkerTask extends DefaultTask {
                def list = $list
                def outputFileDirPath = "${outputFileDirPath}/\${name}"
                def additionalForkOptions = {}
                def runnableClass = TestRunnable.class
                def additionalClasspath = project.layout.files()
                def foo = new Foo()
                def displayName = null
                def isolationMode = IsolationMode.AUTO
                def forkMode = null

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void executeTask() {
                    workerExecutor.submit(runnableClass) {
                        isolationMode = this.isolationMode
                        displayName = this.displayName
                        if (isolationMode == IsolationMode.PROCESS) {
                            forkOptions.maxHeapSize = "64m"
                        }
                        forkOptions(additionalForkOptions)
                        classpath(additionalClasspath)
                        params = [ list.collect { it as String }, new File(outputFileDirPath), foo ]
                        if (this.forkMode != null) {
                            forkMode = this.forkMode
                        }
                    }
                }
            }
        """
    }

    String getRunnableThatCreatesFiles() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import org.gradle.test.FileHelper;
            import java.util.UUID;
            import javax.inject.Inject;

            public class TestRunnable implements Runnable {
                private final List<String> files;
                protected final File outputDir;
                private final Foo foo;
                private static final String id = UUID.randomUUID().toString();

                @Inject
                public TestRunnable(List<String> files, File outputDir, Foo foo) {
                    this.files = files;
                    this.outputDir = outputDir;
                    this.foo = foo;
                }

                public void run() {
                    for (String name : files) {
                        File outputFile = new File(outputDir, name);
                        FileHelper.write(id, outputFile);
                    }
                }
            }
        """
    }

    String getRunnableThatFails(Class<? extends RuntimeException> exceptionClass = RuntimeException.class, String message = "Failure from runnable") {
        return """
            public class RunnableThatFails implements Runnable {
                private final File outputDir;
                
                @javax.inject.Inject
                public RunnableThatFails(List<String> files, File outputDir, Foo foo) { 
                    this.outputDir = outputDir;
                }

                public void run() {
                    try {
                        throw new ${exceptionClass.name}("$message");
                    } finally {
                        outputDir.mkdirs();
                        new File(outputDir, "finished").createNewFile();
                    }                    
                }
            }
        """
    }

    void withParameterClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/other/Foo.java") << """
            package org.gradle.other;

            import java.io.Serializable;

            public class Foo implements Serializable { }
        """
    }

    void withFileHelperClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/test/FileHelper.java") << """
            $fileHelperClass
        """
    }

    void withRunnableClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/test/TestRunnable.java") << """
            package org.gradle.test;

            $runnableThatCreatesFiles
        """

        addImportToBuildScript("org.gradle.test.TestRunnable")
    }

    void withRunnableClassInBuildScript() {
        buildFile << """
            $runnableThatCreatesFiles
        """
    }

    void withJava7CompatibleClasses() {
        file('buildSrc/build.gradle') << """
            tasks.withType(JavaCompile) {
                sourceCompatibility = "1.7"
                targetCompatibility = "1.7"
            }
        """
    }

    String getParameterClass() {
        return """
            package org.gradle.other;

            import java.io.Serializable;

            public class Foo implements Serializable { }
        """
    }

    String getFileHelperClass() {
        return """
            package org.gradle.test;
            
            import java.io.File;
            import java.io.PrintWriter;
            import java.io.BufferedWriter;
            import java.io.FileWriter;
            
            public class FileHelper {
                static void write(String id, File outputFile) {
                    PrintWriter out = null;
                    try {
                        outputFile.getParentFile().mkdirs();
                        outputFile.createNewFile();
                        out = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
                        out.print(id);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (out != null) {
                            out.close();
                        }
                    }
                }
            }
        """
    }

    void addImportToBuildScript(String className) {
        buildFile.text = """
            import ${className}
            ${buildFile.text}
        """
    }

    String getAlternateRunnable() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;
            import javax.inject.Inject;

            public class AlternateRunnable extends TestRunnable {
                @Inject
                public AlternateRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }
            }
        """
    }

    def getBuildFile() {
        return file("build.gradle")
    }

    private def file(Object... path) {
        temporaryFolder.file(path)
    }
}
