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

package org.gradle.launcher.bootstrap

import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.tasks.TaskInputFilePropertyBuilderInternal
import org.gradle.api.tasks.TaskInputFilePropertyBuilder
import org.gradle.api.tasks.TaskInputs
import org.gradle.api.tasks.TaskOutputs
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import javax.tools.ToolProvider

@Subject(MixInCoreTypesTransformingClassLoader)
class MixInCoreTypesTransformingClassLoaderTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def classesDir = tmpDir.file("classes").createDir()
    def srcDir = tmpDir.file("source").createDir()

    def "Task.getInputs() and getOutputs() are bridged with internal return types leaked in Gradle 3.2 and 0.9"() {
        def taskInternalTypeName = "org.gradle.api.internal.TaskInternal"
        def abstractTaskTypeName = "org.gradle.api.internal.AbstractTask"

        putSourceFile("TaskInputs.java", """
            package org.gradle.api.tasks;
            
            public interface TaskInputs {}
        """)

        putSourceFile("TaskOutputs.java", """
            package org.gradle.api.tasks;
            
            public interface TaskOutputs {}
        """)

        putSourceFile("TaskInputsInternal.java", """
            package org.gradle.api.internal;
            
            public interface TaskInputsInternal extends org.gradle.api.tasks.TaskInputs {}
        """)

        putSourceFile("TaskOutputsInternal.java", """
            package org.gradle.api.internal;
            
            public interface TaskOutputsInternal extends org.gradle.api.tasks.TaskOutputs {}
        """)

        putSourceFile("Task.java", """
            package org.gradle.api;
            
            import org.gradle.api.tasks.*;
            
            public interface Task {
                TaskInputs getInputs();
                TaskOutputs getOutputs();
            }
        """)

        putSourceFile("TaskInternal.java", """
            package org.gradle.api.internal;

            public interface TaskInternal extends org.gradle.api.Task {
                @Override
                TaskInputsInternal getInputs();
                @Override
                TaskOutputsInternal getOutputs();
            }
        """)

        putSourceFile("TaskInputFilePropertyBuilder.java", """
            package org.gradle.api.tasks;
            
            public interface TaskInputFilePropertyBuilder {}
        """)

        putSourceFile("TaskInputFilePropertyBuilderInternal.java", """
            package org.gradle.api.internal.tasks;
            
            public interface TaskInputFilePropertyBuilderInternal extends org.gradle.api.tasks.TaskInputFilePropertyBuilder {}
        """)

        putSourceFile("AbstractTask.java", """
            package org.gradle.api.internal;

            public abstract class AbstractTask implements org.gradle.api.internal.TaskInternal {
            }
        """)

        putSourceFile("MyTask.java", """
            package org.gradle.test;

            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.internal.*;

            public class MyTask extends org.gradle.api.internal.AbstractTask {
                @Override
                public TaskInputsInternal getInputs() {
                    return new TaskInputsInternal() {};
                }

                @Override
                public TaskOutputsInternal getOutputs() {
                    return new TaskOutputsInternal() {};
                }
            }
        """)

        compileAll()

        def loader = new MixInCoreTypesTransformingClassLoader(groovyClassLoader, new DefaultClassPath(classesDir))

        when:
        def taskInternalType = loader.loadClass(taskInternalTypeName)
        then:
        // Internal return type leaked in Gradle 3.2
        assertReturnTypesFor taskInternalType, "getInputs", TaskInputs, TaskInputsInternal
        // Internal return type leaked in Gradle 0.9
        assertReturnTypesFor taskInternalType, "getOutputs", TaskOutputs, TaskOutputsInternal

        when:
        def abstractTaskType = loader.loadClass(abstractTaskTypeName)
        then:
        // Internal return type leaked in Gradle 3.2
        assertReturnTypesFor abstractTaskType, "getInputs", TaskInputs, TaskInputsInternal
        // Internal return type leaked in Gradle 0.9
        assertReturnTypesFor abstractTaskType, "getOutputs", TaskOutputs, TaskOutputsInternal

        when:
        def taskInputsInternalType = loader.loadClass("org.gradle.api.internal.TaskInputsInternal")
        def taskOutputsInternalType = loader.loadClass("org.gradle.api.internal.TaskOutputsInternal")
        def myTask = loader.loadClass("org.gradle.test.MyTask").newInstance()

        then:
        taskInputsInternalType.isInstance(myTask.inputs)
        taskOutputsInternalType.isInstance(myTask.outputs)
    }

    def "TaskInputsInternal methods return covariant TaskInputFilePropertyBuilderInternal leaked in Gradle 3.2"() {
        putSourceFile("TaskInputFilePropertyBuilder.java", """
            package org.gradle.api.tasks;
            
            public interface TaskInputFilePropertyBuilder {}
        """)

        putSourceFile("TaskInputFilePropertyBuilderInternal.java", """
            package org.gradle.api.internal.tasks;
            
            public interface TaskInputFilePropertyBuilderInternal extends org.gradle.api.tasks.TaskInputFilePropertyBuilder {}
        """)

        putSourceFile("TaskInputs.java", """
            package org.gradle.api.tasks;
            
            public interface TaskInputs {
                TaskInputFilePropertyBuilder files(Object... paths);
                TaskInputFilePropertyBuilder file(Object path);
                TaskInputFilePropertyBuilder dir(Object dirPath);
            }
        """)

        putSourceFile("TaskInputsInternal.java", """
            package org.gradle.api.internal;
            
            public interface TaskInputsInternal extends org.gradle.api.tasks.TaskInputs {}
        """)

        putSourceFile("DefaultTaskInputs.java", """
            package org.gradle.api.internal;
            
            import org.gradle.api.tasks.*;
            import org.gradle.api.internal.tasks.*;
            
            public class DefaultTaskInputs implements TaskInputsInternal {
                @Override
                public TaskInputFilePropertyBuilder files(Object... paths) {
                    return new TaskInputFilePropertyBuilderInternal() {};
                }
                
                @Override
                public TaskInputFilePropertyBuilder file(Object path) {
                    return new TaskInputFilePropertyBuilderInternal() {};
                }
                
                @Override
                public TaskInputFilePropertyBuilder dir(Object dirPath) {
                    return new TaskInputFilePropertyBuilderInternal() {};
                }
            }
        """)

        compileAll()

        def loader = new MixInCoreTypesTransformingClassLoader(groovyClassLoader, new DefaultClassPath(classesDir))

        when:
        def defaultTaskInputsType = loader.loadClass("org.gradle.api.internal.DefaultTaskInputs")
        then:
        // Internal return types leaked in Gradle 3.2
        assertReturnTypesFor defaultTaskInputsType, "dir", TaskInputFilePropertyBuilder, TaskInputFilePropertyBuilderInternal
        assertReturnTypesFor defaultTaskInputsType, "file", TaskInputFilePropertyBuilder, TaskInputFilePropertyBuilderInternal
        assertReturnTypesFor defaultTaskInputsType, "files", TaskInputFilePropertyBuilder, TaskInputFilePropertyBuilderInternal
    }

    ClassLoader getGroovyClassLoader() {
        def spec = new FilteringClassLoader.Spec()
        spec.allowPackage("groovy")
        return new FilteringClassLoader(getClass().classLoader, spec)
    }

    private void putSourceFile(String fileName, String text) {
        srcDir.file(fileName).text = text
    }

    private static void assertReturnTypesFor(Class type, String methodName, Class... returnTypes) {
        assert type.methods.findAll { it.name == methodName }*.returnType.name.sort() == returnTypes*.name.sort()
    }

    private void compileAll() {
        def compiler = ToolProvider.systemJavaCompiler
        def fileManager = compiler.getStandardFileManager(null, null, null)
        def task = compiler.getTask(null, fileManager, null, ["-d", classesDir.path], null, fileManager.getJavaFileObjects(srcDir.listFiles()))
        task.call()
    }
}
