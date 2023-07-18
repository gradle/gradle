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
import org.gradle.util.internal.TextUtil

class WorkerExecutorFixture {
    public static final ISOLATION_MODES = ["'noIsolation'", "'classLoaderIsolation'", "'processIsolation'"]
    def outputFileDir
    def outputFileDirPath
    def list = [ 1, 2, 3 ]
    private final TestNameTestDirectoryProvider temporaryFolder
    final WorkParameterClass testParameterType
    final WorkActionClass workActionThatCreatesFiles
    final WorkActionClass workActionThatFails

    WorkerExecutorFixture(TestNameTestDirectoryProvider temporaryFolder) {
        this.temporaryFolder = temporaryFolder
        this.outputFileDir = temporaryFolder.file("build/workers")
        outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)

        testParameterType = workParameterClass("TestParameters", "org.gradle.test")
        testParameterType.imports += ["java.io.File", "java.util.List", "org.gradle.other.Foo"]
        testParameterType.fields += [
                "files": "List<String>",
                "outputDir": "File",
                "bar": "Foo"
        ]

        workActionThatCreatesFiles = getWorkActionThatCreatesFiles("TestWorkAction")

        workActionThatFails = getWorkActionThatFails(RuntimeException.class, "Failure from work action")
    }

    def prepareTaskTypeUsingWorker() {
        withParameterClassInBuildSrc()
        withFileHelperClassInBuildSrc()

        buildFile << """
            import org.gradle.workers.*
            $taskTypeUsingWorker
        """
    }

    String getTaskTypeUsingWorker() {
        return """
            import javax.inject.Inject
            import org.gradle.other.Foo

            class WorkerTask extends DefaultTask {
                @Internal
                def list = $list
                @Internal
                def outputFileDirPath = "${outputFileDirPath}/\${name}"
                @Internal
                def additionalForkOptions = {}
                @Internal
                def workActionClass = TestWorkAction.class
                @Internal
                def additionalClasspath = project.layout.files()
                @Internal
                def foo = new Foo()
                @Internal
                def displayName = null
                @Internal
                def isolationMode = 'noIsolation'
                @Internal
                def forkMode = null
                @Internal
                def additionalParameters = {}

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void executeTask() {
                    workerExecutor."\${isolationMode}"({ spec ->
                        displayName = this.displayName
                        if (spec instanceof ClassLoaderWorkerSpec) {
                            classpath.from(additionalClasspath)
                        }
                        if (spec instanceof ProcessWorkerSpec) {
                            forkOptions.maxHeapSize = "64m"
                            forkOptions(additionalForkOptions)
                        }
                        if (this.forkMode != null) {
                            forkMode = this.forkMode
                        }
                    }).submit(workActionClass) { parameters ->
                        files = list.collect { it as String }
                        outputDir = new File(outputFileDirPath)
                        bar = foo
                        additionalParameters.call(parameters)
                    }
                }
            }
        """
    }

    WorkActionClass workActionClass(String name, String packageName, WorkParameterClass parameterClass) {
       return new WorkActionClass(name, packageName, parameterClass)
    }

    WorkParameterClass workParameterClass(String name, String packageName) {
        return new WorkParameterClass(name, packageName)
    }

    WorkActionClass getWorkActionThatCreatesFiles(String name) {
        def workerClass = workActionClass(name, "org.gradle.test", testParameterType)
        workerClass.imports += [
                "java.io.File",
                "java.util.UUID"
        ]
        workerClass.extraFields = """
            private static final String id = UUID.randomUUID().toString();
        """
        workerClass.action = """
            for (String name : getParameters().getFiles()) {
                File outputFile = new File(getParameters().getOutputDir(), name);
                org.gradle.test.FileHelper.write(id, outputFile);
            }
        """
        return workerClass
    }

    WorkActionClass getWorkActionThatFails(Class<? extends RuntimeException> exceptionClass, String message) {
        def workerClass = workActionClass("WorkActionThatFails", "org.gradle.test", testParameterType)
        workerClass.imports += ["java.io.File"]
        workerClass.action = """
            try {
                throw new ${exceptionClass.name}("$message");
            } finally {
                getParameters().getOutputDir().mkdirs();
                new File(getParameters().getOutputDir(), "finished").createNewFile();
            }
        """
        return workerClass
    }

    WorkActionClass getBlockingWorkActionThatCreatesFiles(String url) {
        def workerClass = getWorkActionThatCreatesFiles("BlockingWorkAction")
        workerClass.imports += ["java.net.URL"]
        workerClass.action += """
            try {
                new URL("$url/" + getParameters().getOutputDir().getName()).openConnection().getHeaderField("RESPONSE");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        """
        return workerClass
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

    void withWorkActionClassInBuildSrc() {
        workActionThatCreatesFiles.writeToBuildSrc()
    }

    void withWorkActionClassInBuildScript() {
        workActionThatCreatesFiles.writeToBuildFile()
    }

    void withBlockingWorkActionClassInBuildSrc(String url) {
        getBlockingWorkActionThatCreatesFiles(url).writeToBuildSrc()
    }

    void withAlternateWorkActionClassInBuildSrc() {
        alternateWorkAction.writeToBuildSrc()
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
                public static void write(String id, File outputFile) {
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

    TestClass getAlternateWorkAction() {
        String name = "AlternateWorkAction"
        String packageName = "org.gradle.test"
        return new TestClass(name, packageName) {
            @Override
            TestClass writeToBuildFile() {
                workActionThatCreatesFiles.writeToBuildFile()
                super.writeToBuildFile()
                return this
            }

            @Override
            TestClass writeToBuildSrc() {
                addImportToBuildScript("${packageName}.${name.capitalize()}")
                workActionThatCreatesFiles.writeToBuildSrc()
                super.writeToBuildSrc()
                return this
            }

            @Override
            String getBody() {
                return """
                public abstract class ${name.capitalize()} extends TestWorkAction {

                @javax.inject.Inject
                public ${name.capitalize()}() { }
            }
            """
            }
        }
    }

    def getBuildFile() {
        return file("build.gradle")
    }

    private def file(Object... path) {
        temporaryFolder.file(path)
    }

    abstract class TestClass {
        String name
        String packageName
        List<String> imports = []
        boolean writtenToBuildFile = false

        TestClass(String name, String packageName) {
            this.name = name
            this.packageName = packageName
        }

        String getImportDeclarations() {
            String importDeclarations = ""
            imports.each {
                importDeclarations += """
                    import ${it};
                """
            }
            return importDeclarations
        }

        TestClass writeToFile(File file) {
            file.text = """
                package ${packageName};

                ${importDeclarations}

                ${body}
            """
            return this
        }

        TestClass writeToBuildSrc() {
            writeToFile file("buildSrc/src/main/java/${packageName.replace("\\.", "/")}/${name.capitalize()}.java")
            return this
        }

        TestClass writeToBuildFile() {
            if (!writtenToBuildFile) {
                buildFile << """
                    ${importDeclarations}

                    ${body}
                """
                writtenToBuildFile = true
            }
            return this
        }

        abstract String getBody()
    }

    class WorkParameterClass extends TestClass {
        Map<String, String> fields = [:]

        WorkParameterClass(String name, String packageName) {
            super(name, packageName)
            this.imports += ["org.gradle.workers.WorkParameters"]
        }

        String getBody() {
            return """
                public interface ${name} extends WorkParameters {
                    ${fieldDeclarations}
                }
            """
        }

        String getFieldDeclarations() {
            String fieldDeclarations = ""
            fields.each { name, type ->
                fieldDeclarations += """
                    ${type} get${name.capitalize()}();
                """
                if (!(type.startsWith("Property<") || type == "ConfigurableFileCollection")) {
                    fieldDeclarations += """
                        void set${name.capitalize()}(${type} ${name.uncapitalize()});
                    """
                }
            }
            return fieldDeclarations
        }

        WorkParameterClass withFields(Map<String, String> fields) {
            this.fields = fields
            return this
        }
    }

    class WorkActionClass extends TestClass {
        WorkParameterClass parameters
        String extraFields = ""
        String action = ""
        String constructorArgs = ""
        String constructorAction = ""

        WorkActionClass(String name, String packageName, WorkParameterClass parameters) {
            super(name, packageName)
            this.parameters = parameters
            this.imports += ["org.gradle.workers.WorkAction"]
        }

        @Override
        WorkActionClass writeToBuildSrc() {
            parameters.writeToBuildSrc()
            addImportToBuildScript("${packageName}.${name.capitalize()}")
            super.writeToBuildSrc()
            return this
        }

        @Override
        WorkActionClass writeToBuildFile() {
            parameters.writeToBuildFile()
            super.writeToBuildFile()
            return this
        }

        String getBody() {
            return """
                public abstract class ${name.capitalize()} implements WorkAction<${parameters.name.capitalize()}> {
                    ${extraFields}

                    @javax.inject.Inject
                    public ${name.capitalize()}(${constructorArgs}) {
                        ${constructorAction}
                    }

                    public void execute() {
                        ${action}
                    }
                }
            """
        }
    }
}
