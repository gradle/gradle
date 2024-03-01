/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.deployment

import com.gradle.enterprise.testing.annotations.LocalOnly
import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile

@LocalOnly(because = "https://github.com/gradle/gradle-private/issues/3499")
class JavaApplicationDeploymentIntegrationTest extends AbstractContinuousIntegrationTest {
    TestFile messageSrc

    def setup() {
        buildFile << """
            apply plugin: 'java'

            task run(type: ${RunApplication.canonicalName}) {
                classpath = sourceSets.main.runtimeClasspath
                mainClassName = "org.gradle.deployment.Main"
                arguments = [ file("log").absolutePath, file("ready").absolutePath, "Hello, World!" ]
            }
        """

        file("src/main/java/org/gradle/deployment/Main.java") << """
            package org.gradle.deployment;

            import java.io.File;
            import java.io.FileOutputStream;
            import java.io.PrintWriter;
            import java.util.concurrent.CountDownLatch;
            import java.util.concurrent.TimeUnit;

            public class Main {
                public static void main(String... args) throws Exception {
                    PrintWriter writer = new PrintWriter(new FileOutputStream(args[0], true));
                    for (String arg : args) {
                        writer.println(Message.message + " > " + arg);
                    }
                    writer.close();

                    // wait forever
                    new File(args[1]).createNewFile();
                    new CountDownLatch(1).await(10, TimeUnit.MINUTES);
                }
            }
        """
        messageSrc = file("src/main/java/org/gradle/deployment/Message.java") << """
            package org.gradle.deployment;
            public class Message {
                public static final String message = "[APP]";
            }
        """
    }

    def "can run application"() {
        when:
        succeeds("run")
        then:
        assertApplicationReady()
        assertLogHasMessage("[APP] > Hello, World!")
    }

    def "deployment is automatically restarted"() {
        when:
        succeeds("run")
        then:
        assertApplicationReady()
        assertLogHasMessage("[APP] > Hello, World!")

        when:
        file("ready").delete()
        messageSrc.text = messageSrc.text.replace("APP", "NEW")
        buildTriggeredAndSucceeded()
        then:
        assertApplicationReady()
        assertLogHasMessage("[NEW] > Hello, World!")
    }

    def "deployment is not automatically restarted with changeBehavior=NONE"() {
        buildFile << """
            run {
                changeBehavior = "NONE"
            }
        """
        when:
        succeeds("run")
        then:
        assertApplicationReady()
        assertLogHasMessage("[APP] > Hello, World!")

        when:
        messageSrc.text = messageSrc.text.replace("APP", "NEW")
        buildTriggeredAndSucceeded()
        then:
        assertLogDoesNotHasMessage("[NEW] > Hello, World!")
    }

    void assertApplicationReady() {
        ConcurrentTestUtil.poll {
            file("ready").assertIsFile()
        }
    }

    void assertLogHasMessage(String message) {
        assert file("log").text.contains(message)
    }

    void assertLogDoesNotHasMessage(String message) {
        assert !file("log").text.contains(message)
    }
}
