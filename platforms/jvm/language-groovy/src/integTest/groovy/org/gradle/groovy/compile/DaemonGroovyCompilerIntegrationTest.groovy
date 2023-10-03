/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.junit.Assume

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class DaemonGroovyCompilerIntegrationTest extends AbstractApiGroovyCompilerIntegrationSpec {
    def "respects fork options settings and ignores executable"() {
        Jvm differentJvm = AvailableJavaHomes.differentJdkWithValidJre
        Assume.assumeNotNull(differentJvm)
        def differentJavacExecutablePath = normaliseFileSeparators(differentJvm.javacExecutable.absolutePath)

        file("src/main/groovy/JavaThing.java") << "public class JavaThing {}"
        file("src/main/groovy/AbstractThing.groovy") << "class AbstractThing {}"
        file("src/main/groovy/Thing.groovy") << "class Thing extends AbstractThing {}"

        buildFile << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.internal.jvm.Jvm

            apply plugin: "groovy"
            ${mavenCentralRepository()}
            tasks.withType(GroovyCompile) {
                options.forkOptions.executable = "${differentJavacExecutablePath}"
                options.forkOptions.memoryInitialSize = "128m"
                options.forkOptions.memoryMaximumSize = "256m"
                options.forkOptions.jvmArgs = ["-Dfoo=bar"]

                doLast {
                    assert services.get(WorkerDaemonClientsManager).idleClients.find {
                        new File(it.forkOptions.javaForkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.javaForkOptions.minHeapSize == "128m" &&
                        it.forkOptions.javaForkOptions.maxHeapSize == "256m" &&
                        it.forkOptions.javaForkOptions.systemProperties['foo'] == "bar"
                    }
                }
            }
        """

        expect:
        succeeds "compileGroovy"
        groovyClassFile("Thing.class").exists()
        groovyClassFile("JavaThing.class").exists()
    }

    @Override
    String compilerConfiguration() {
        "tasks.withType(GroovyCompile) { groovyOptions.fork = true }"
    }
}
