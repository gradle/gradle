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

class TestTaskJvmArgsProviderIntegrationTest extends AbstractIntegrationSpec {

    def "jvm argument providers are passed to the test worker"() {
        file("src/test/java/FooTest.java") << """
            import java.io.*;
            import org.junit.*;

            public class FooTest {
                @Test
                public void test() throws IOException {
                    String location = System.getProperty("input.path");
                    BufferedReader reader = new BufferedReader(new FileReader(location));
                    String input = reader.readLine();
                    reader.close();
                    Assert.assertEquals("Test", input);
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}

            dependencies {
                testImplementation "$testJunitCoordinates"
            }

            class MyTestSystemProperties implements CommandLineArgumentProvider {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile

                @Override
                List<String> asArguments() {
                    ["-Dinput.path=\${inputFile.absolutePath}"]
                }
            }

            test.jvmArgumentProviders << new MyTestSystemProperties(
                inputFile: file(providers.gradleProperty('inputFile').get())
            )
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
        failure.assertHasResolutions("Run with --scan to get full insights.")
    }
}
