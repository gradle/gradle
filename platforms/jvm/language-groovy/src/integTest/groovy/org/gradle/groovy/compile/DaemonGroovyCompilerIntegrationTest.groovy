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
                        new File(it.forkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.jvmOptions.minHeapSize == "128m" &&
                        it.forkOptions.jvmOptions.maxHeapSize == "256m" &&
                        it.forkOptions.jvmOptions.mutableSystemProperties['foo'] == "bar"
                    }
                }
            }
        """

        expect:
        succeeds "compileGroovy"
        groovyClassFile("Thing.class").exists()
        groovyClassFile("JavaThing.class").exists()
    }

    def "setting forkOptions is deprecated"() {
        given:
        file("src/main/groovy/Thing.groovy") << "class Thing {}"
        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}
            tasks.withType(GroovyCompile) {
                // Just do something trivial to call it
                options.setForkOptions(options.forkOptions)
            }
        """
        executer.expectDocumentedDeprecationWarning("The CompileOptions.setForkOptions(ForkOptions) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Setting a new instance of forkOptions is unnecessary. Please use the forkOptions(Action) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_nested_properties_setters")

        expect:
        succeeds "compileGroovy"
    }

    @Override
    String compilerConfiguration() {
        "tasks.withType(GroovyCompile) { groovyOptions.fork = true }"
    }
}
