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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.ToBeImplemented
import spock.lang.Unroll

class TestTaskJvmArgsProviderIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/test/java/FooTest.java") << """
            import java.io.*;
            import org.junit.*;
            import java.lang.*;

            public class FooTest {
                @Test
                public void checkInputFile() throws IOException {
                    String location = System.getProperty("input.file.path");
                    Assume.assumeNotNull(location);
                    String input = readText(new File(location));
                    Assert.assertEquals("Test", input);
                }
                
                @Test
                public void checkInputDirectory() throws IOException {
                    String location = System.getProperty("input.directory.path");
                    Assume.assumeNotNull(location);
                    String input = readText(new File(location, "file.txt"));
                    Assert.assertEquals("Test", input);
                }
                
                @Test
                public void checkOutputFile() throws IOException {
                    String location = System.getProperty("output.file.path");
                    Assume.assumeNotNull(location);
                    File outputFile = new File(location);
                    Assert.assertTrue(outputFile.getParentFile().isDirectory());
                    writeText(outputFile, "Test");
                }
                
                @Test
                public void checkOutputDirectory() throws IOException {
                    String location = System.getProperty("output.directory.path");
                    Assume.assumeNotNull(location);
                    File outputDirectory = new File(location);
                    Assert.assertTrue(outputDirectory.isDirectory());
                    System.out.println(new File(outputDirectory, "file.txt").getAbsolutePath());
                    writeText(new File(outputDirectory, "file.txt"), "Test");
                }
                
                private String readText(File file) throws IOException {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    try {
                        return reader.readLine();
                    } finally {
                        reader.close();
                    }
                }
                
                private void writeText(File file, String text) throws IOException {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                    try {
                        writer.write(text);
                    } finally {
                        writer.close();
                    }
                }
            }
        """

        file("build.gradle") << """
            apply plugin: "java"

            ${mavenCentralRepository()}

            dependencies {
                testCompile "junit:junit:4.12"
            }              
        """
    }

    def "jvm argument providers are passed to the test worker"() {
        file("build.gradle") << """
            class MyTestSystemProperties implements CommandLineArgumentProvider {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile

                @Override
                List<String> asArguments() {
                    ["-Dinput.file.path=\${inputFile.absolutePath}"]
                }
            }

            test.jvmArgumentProviders << new MyTestSystemProperties(inputFile: file(project.property('inputFile')))
        """
        file('inputFile.txt').text = "Test"

        expect:
        succeeds "test", "-PinputFile=inputFile.txt"

        when:
        file('different-file.txt').text = "Test"
        file("inputFile.txt").delete()
        run "test", "-PinputFile=different-file.txt"

        then:
        skipped(":test")

        when:
        file('different-file.txt').text = "Not Test"

        then:
        fails "test", "-PinputFile=different-file.txt"
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("There were failing tests.")
    }

    @Unroll
    def "can pass inputs as system properties via #value"() {
        buildFile << """
            test.systemProperties {
                add '${systemPropertyName}', ${value}
            }
        """

        def original = isDirectory ? file('directory').create {
            file('file.txt').text = "Test"
        } : file('original-file.txt')
        def moved = isDirectory ? file('moved-directory').create {
            file('file.txt').text = "Test"
        } : file('other-location.txt')
        def changed = isDirectory ? file('changed-directory').create {
            file('file.txt').text = "changed"
        } : file('changed-file.txt')
        file('original-file.txt').text = "Test"
        file('other-location.txt').text = "Test"
        file('changed-file.txt').text = "changed"

        when:
        run 'test', "-PfilePath=${original}"
        then:
        executedAndNotSkipped ':test'

        when:
        run 'test', "-PfilePath=${moved}"
        then:
        skipped ':test'

        when:
        fails 'test', "-PfilePath=${changed}"
        then:
        executedAndNotSkipped ':test'
        failure.assertHasCause("There were failing tests.")

        where:
        systemPropertyName     | value                      | isDirectory
        'input.file.path'      | 'inputFile(filePath)'      | false
        'input.directory.path' | 'inputDirectory(filePath)' | true
    }

    @Unroll
    def "can pass outputs as system properties via #value"() {
        buildFile << """
            test.systemProperties {
                add '${systemPropertyName}', ${value}
            }
        """

        def output = isDirectory ? file('directory') : file('output-file.txt')

        def outputFile = isDirectory ? output.file('file.txt') : output

        when:
        run 'test', "-PfilePath=${output}"
        then:
        executedAndNotSkipped ':test'
        outputFile.text == 'Test'

        when:
        run 'test', "-PfilePath=${output}"
        then:
        skipped ':test'

        when:
        outputFile.text = "changed"
        run 'test', "-PfilePath=${output}"
        then:
        executedAndNotSkipped ':test'
        outputFile.text == 'Test'

        where:
        systemPropertyName      | value                       | isDirectory
        'output.file.path'      | 'outputFile(filePath)'      | false
        'output.directory.path' | 'outputDirectory(filePath)' | true
    }

    @Unroll
    def "can pass #type as ignored system property"() {
        buildFile << """
            test.systemProperties {
                add 'path.ignored', ignored(${expression})
            }
        """

        when:
        run 'test', "-PpropertyValue=${value}"
        then:
        executedAndNotSkipped ':test'

        when:
        run 'test', "-PpropertyValue=${changedValue}"
        then:
        skipped ':test'

        where:
        type | expression | value | changedValue
        'file'   | 'file(propertyValue)'            | 'ignored.txt' | 'ignored2.text'
        'object' | 'Integer.valueOf(propertyValue)' | '25'          | '38'
    }

    def "can pass providers as system properties"() {
        buildFile << """
            test.systemProperties {
                add 'path.ignored', ignored(layout.projectDirectory.file(provider { "ignored.txt" }))
                add 'output.file.path', outputFile(layout.buildDirectory.file("output.txt"))
                add 'output.directory.path', outputDirectory(layout.buildDirectory.dir("output"))
                add 'input.file.path', inputFile(layout.projectDirectory.file("input.txt"))
                add 'input.directory.path', inputDirectory(layout.projectDirectory.dir("input"))
            }
        """
        file('input.txt').text = "Test"
        file("input/file.txt").text = "Test"

        when:
        run 'test'
        then:
        executedAndNotSkipped ':test'
    }

    @ToBeImplemented("Requires https://github.com/gradle/gradle/pull/4665")
    def "passing providers as system properties retain dependencies"() {
        buildFile << """         
            class Producer extends DefaultTask {
                @OutputDirectory
                final DirectoryProperty outputDir = newOutputDirectory()
                
                @TaskAction
                void createOutput() {
                    new File(outputDir.get().asFile, "file.txt").text = "Test"
                }
            }

            task producer(type: Producer) {
                outputDir.set(layout.buildDirectory.dir("output"))
            }
            
            test.systemProperties {
                add 'input.directory.path', inputDirectory(producer.outputDir)
            }
        """

        when:
        // FIXME: should succeed
        fails 'test'
        then:
        // FIXME: executedAndNotSkipped ':producer', ':test'
        executedAndNotSkipped ':test'
        notExecuted ':producer'
        failure.assertHasCause"Directory '${file('build/output').absolutePath}' specified for property 'jvmArgumentProviders.input.directory.path\$0.value.value' does not exist."
    }

    def "can pass callables as system properties"() {
        buildFile << """
            test.systemProperties {
                add 'path.ignored', ignored { "ignored.txt" }
                add 'output.file.path', outputFile { "output.txt" }
                add 'output.directory.path', outputDirectory { "output" }
                add 'input.file.path', inputFile { "input.txt" }
                add 'input.directory.path', inputDirectory { "input" }
            }
        """
        file('input.txt').text = "Test"
        file("input/file.txt").text = "Test"

        when:
        run 'test'
        then:
        executedAndNotSkipped ':test'
    }
}
