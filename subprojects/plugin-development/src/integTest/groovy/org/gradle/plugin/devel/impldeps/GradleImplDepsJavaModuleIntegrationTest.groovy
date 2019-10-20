/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugin.devel.impldeps

import static java.util.concurrent.TimeUnit.SECONDS

import static java.io.File.separator
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

class GradleImplDepsJavaModuleIntegrationTest extends BaseGradleImplDepsTestCodeIntegrationTest {


    def setup() {
        executer.requireOwnGradleUserHomeDir()
        buildFile << testablePluginProject(applyJavaPlugin())
    }

    @Issue("https://github.com/gradle/gradle/issues/11027")
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "JPMS is able to derive module descriptor for gradle api jar as an automatic module"() {
        setup:
        productionCode()

        def version = distribution.version.version
        def generatedJarsDirectory = "user-home/caches/$version/generated-gradle-jars"

        when:
        succeeds("classes")

        def gradleApiJar = file("$generatedJarsDirectory/gradle-api-${version}.jar")

        def modulePath = gradleApiJar.getCanonicalPath()

        then:
        gradleApiJar.assertExists()


        when:
        ProcessBuilder processBuilder = new ProcessBuilder();

        processBuilder.command(toCommand(modulePath))

        then:
        Process process = processBuilder.start()

        and:
        process.inputStream.text.contains("No module descriptor found. Derived automatic module")
    }

    private String[] toCommand(String modulePath){
        return [Jvm.current().javaHome.absolutePath + separator + "bin"  + separator + "jar", "-f", "\"" + modulePath + "\"", "--describe-module"]
    }
}
