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

import org.gradle.integtests.fixtures.jvm.JvmUtils
import org.junit.Assume

import static org.gradle.util.TextUtil.normaliseFileSeparators

class DaemonGroovyCompilerIntegrationTest extends ApiGroovyCompilerIntegrationSpec {
    def "setting executable on java does not affect groovy compilation"() {
        def differentJvm = JvmUtils.findAnotherJvm()
        Assume.assumeNotNull(differentJvm)
        def differentJavaExecutablePath = normaliseFileSeparators(differentJvm.javacExecutable.absolutePath)

        file("src/main/groovy/JavaThing.java") << "public class JavaThing {}"
        file("src/main/groovy/AbstractThing.groovy") << "class AbstractThing {}"
        file("src/main/groovy/Thing.groovy") << "class Thing extends AbstractThing {}"

        buildFile << """
            apply plugin: "groovy"
            repositories { mavenCentral() }
            tasks.withType(GroovyCompile) {
                options.forkOptions.executable = "${differentJavaExecutablePath}"
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

    @Override
    String checkCompileOutput(String errorMessage) {
        true
    }
}
