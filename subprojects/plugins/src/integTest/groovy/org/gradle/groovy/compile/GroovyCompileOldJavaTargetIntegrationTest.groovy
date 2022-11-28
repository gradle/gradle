/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

class GroovyCompileOldJavaTargetIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def "can compile source to Java target #javaVersion"() {
        def jdk11 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)

        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}

            ${javaPluginToolchainVersion(jdk11)}

            java {
                sourceCompatibility = "${javaVersion}"
                targetCompatibility = "${javaVersion}"
            }

            dependencies {
                implementation "${groovyModuleDependency("groovy", "2.5.8")}"
            }
        """

        file("src/main/groovy/JavaThing.java") << "public class JavaThing {}"
        file("src/main/groovy/GroovyBar.groovy") << "public class GroovyBar { def bar() {} }"

        when:
        withInstallations(jdk11).run(":compileGroovy")

        then:
        executedAndNotSkipped(":compileGroovy")

        JavaVersion.forClass(groovyClassFile("JavaThing.class").bytes) == javaVersion
        JavaVersion.forClass(groovyClassFile("GroovyBar.class").bytes) == javaVersion

        where:
        javaVersion << ["1.6", "1.7", "1.8"].collect { JavaVersion.toVersion(it) }
    }
}
