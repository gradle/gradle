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
package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import spock.lang.Issue

import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.javaClassWithNewLineAtEnd
import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.simpleCheckStyleConfig

class CheckstylePluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "check"
    }

    def setup() {
        buildFile << """
            apply plugin: 'java'

            // Necessary to make CC tests pass, though it appears unused here
            ${mavenCentralRepository()}

            dependencies { implementation localGroovy() }

        """
    }

    @Issue("https://github.com/gradle/gradle/issues/21301")
    def "can pass a URL in configProperties"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            checkstyle {
                configProperties["some"] = new URL("https://gradle.org/")
            }
        """

        file('src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        when:
        succeeds 'check'

        then:
        executedAndNotSkipped ':checkstyleMain'
    }

    def "can configure checkstyle via both facades"() {
        goodCode()
        writeDummyConfig()
        buildFile.text = """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.internal.jvm.Jvm

            plugins {
                id("java")
                id("checkstyle")
            }

            repositories {
                ${mavenCentralRepository()}
            }

            tasks.named('checkstyleMain', Checkstyle).configure {
                minHeapSize.set("122m")
                maxHeapSize.set("255m")
            }

            tasks.named('checkstyleMain', org.gradle.api.plugins.quality.v2.CheckstyleV2).configure {
                doLast {
                    // We register do doLast on CheckstyleV2, since we don't have translations for doLast yet
                    assert services.get(WorkerDaemonClientsManager).idleClients.find {
                        println("checkstyleMain.forkOptions.jvmOptions.minHeapSize == \${it.forkOptions.jvmOptions.minHeapSize}")
                        println("checkstyleMain.forkOptions.jvmOptions.maxHeapSize == \${it.forkOptions.jvmOptions.maxHeapSize}")
                        new File(it.forkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.jvmOptions.minHeapSize == "122m" &&
                        it.forkOptions.jvmOptions.maxHeapSize == "255m"
                    }
                }
            }

            tasks.named('checkstyleTest', org.gradle.api.plugins.quality.v2.CheckstyleV2).configure {
                minHeapSize.set("256m")
                maxHeapSize.set("355m")

                doLast {
                    assert services.get(WorkerDaemonClientsManager).idleClients.find {
                        println("checkstyleTest.forkOptions.jvmOptions.minHeapSize == \${it.forkOptions.jvmOptions.minHeapSize}")
                        println("checkstyleTest.forkOptions.jvmOptions.maxHeapSize == \${it.forkOptions.jvmOptions.maxHeapSize}")
                        new File(it.forkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.jvmOptions.minHeapSize == "256m" &&
                        it.forkOptions.jvmOptions.maxHeapSize == "355m"
                    }
                }
            }
        """

        expect:
        succeeds("checkstyleMain", "checkstyleTest")
        outputContains("checkstyleMain.forkOptions.jvmOptions.minHeapSize == 122m")
        outputContains("checkstyleMain.forkOptions.jvmOptions.maxHeapSize == 255m")
        outputContains("checkstyleTest.forkOptions.jvmOptions.minHeapSize == 256m")
        outputContains("checkstyleTest.forkOptions.jvmOptions.maxHeapSize == 355m")
    }

    private void goodCode() {
        file('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        file('src/test/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
    }

    private void writeDummyConfig() {
        file('config/checkstyle/checkstyle.xml') << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
</module>
        """
    }
}
