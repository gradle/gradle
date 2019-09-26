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

package org.gradle.integtests.tooling.r51

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.work.WorkItemOperationDescriptor

@ToolingApiVersion('>=5.1')
@TargetGradleVersion('>=5.1')
class WorkItemProgressEventCrossVersionSpec extends ToolingApiSpecification {

    void setup() {
        prepareTaskTypeUsingWorker()
        withRunnableClassInBuildSrc()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                displayName = "Test Work"
            }
        """
    }

    def "reports typed work item progress events as descendants of tasks"() {
        when:
        def events = runBuild("runInWorker", EnumSet.allOf(OperationType))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.task
        with(taskOperation.descendant("Test Work")) {
            successful
            workItem
            descriptor.className == "org.gradle.test.TestRunnable"
        }
    }

    @TargetGradleVersion('>=4.8 <5.1') // fixture uses ProjectLayout.files()
    def "reports generic work item progress events as descendants of tasks"() {
        when:
        def events = runBuild("runInWorker", EnumSet.allOf(OperationType))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.task
        with(taskOperation.descendant("Test Work")) {
            successful
            buildOperation
        }
    }

    def "does not report work item progress events when WORK_ITEM operations are not requested"() {
        when:
        def events = runBuild("runInWorker", EnumSet.complementOf(EnumSet.of(OperationType.WORK_ITEM)))

        then:
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.task
        taskOperation.descendants { it.descriptor.displayName == "Test Work" }.empty
    }

    def "does not report work item progress events when TASK operations are not requested"() {
        when:
        def events = runBuild("runInWorker", EnumSet.of(OperationType.WORK_ITEM))

        then:
        events.empty
    }

    def "includes failure in progress event"() {
        given:
        buildFile << """
            ${getRunnableThatFails(IllegalStateException, "something went horribly wrong")}
            runInWorker {
                displayName = null
                runnableClass = RunnableThatFails
            }
        """

        when:
        def events = ProgressEvents.create()
        runBuild("runInWorker", events, EnumSet.of(OperationType.TASK, OperationType.WORK_ITEM))

        then:
        thrown(BuildException)
        def taskOperation = events.operation("Task :runInWorker")
        taskOperation.task
        with(taskOperation.child("RunnableThatFails")) {
            !successful
            workItem
            descriptor instanceof WorkItemOperationDescriptor
            descriptor.className == "RunnableThatFails"
            failures.size() == 1
            with (failures[0]) {
                message == "something went horribly wrong"
                description.startsWith("java.lang.IllegalStateException: something went horribly wrong")
            }
        }
    }

    private ProgressEvents runBuild(String task, Set<OperationType> operationTypes) {
        ProgressEvents events = ProgressEvents.create()
        runBuild(task, events, operationTypes)
        events
    }

    private Object runBuild(String task, ProgressListener listener, Set<OperationType> operationTypes) {
        withConnection {
            newBuild()
                .forTasks(task)
                .addProgressListener(listener, operationTypes)
                .run()
        }
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

        def outputFileDir = temporaryFolder.file("build/workers")
        def outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)

        return """
            import javax.inject.Inject
            import org.gradle.other.Foo

            class WorkerTask extends DefaultTask {
                @Internal
                def list = [1, 2, 3]
                @Internal
                def outputFileDirPath = "${outputFileDirPath}/\${name}"
                @Internal
                def additionalForkOptions = {}
                @Internal
                def runnableClass = TestRunnable.class
                @Internal
                def additionalClasspath = project.layout.files()
                @Internal
                def foo = new Foo()
                @Internal
                def displayName = null
                @Internal
                def isolationMode = IsolationMode.AUTO
                @Internal
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

    void withRunnableClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/test/TestRunnable.java") << """
            package org.gradle.test;
            $runnableThatCreatesFiles
        """

        addImportToBuildScript("org.gradle.test.TestRunnable")
    }

    void addImportToBuildScript(String className) {
        buildFile.text = """
            import ${className}
            ${buildFile.text}
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
}
