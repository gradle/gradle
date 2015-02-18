/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.jvm.JavaHomeException
import org.gradle.internal.jvm.JavaInfo
import org.gradle.util.TextUtil
import spock.lang.IgnoreIf
import spock.lang.Issue

@IgnoreIf({ GradleContextualExecuter.parallel })
class JavaCompileParallelIntegrationTest extends AbstractIntegrationSpec {
    RandomJdkPicker randomJdkPicker = new RandomJdkPicker()

    @Issue("https://issues.gradle.org/browse/GRADLE-3029")
    def "system property java.home is not modified across compile task boundaries"() {
        def projectNames = ['a', 'b', 'c', 'd', 'e', 'f', 'g']

        settingsFile << "include ${projectNames.collect { "'$it'" }.join(', ')}"
        buildFile << """
            subprojects {
                apply plugin: 'java'

                repositories {
                    mavenCentral()
                }

                dependencies {
                    compile 'commons-lang:commons-lang:2.5'
                }
            }
"""

        projectNames.each { projectName ->
            buildFile << """
project(':$projectName') {
    tasks.withType(JavaCompile) {
        options.with {
            fork = true
            forkOptions.executable = "$randomJdkPicker.javacExecutable"
        }
    }
}
"""
            file("${projectName}/src/main/java/Foo.java") << """
import org.apache.commons.lang.StringUtils;

public class Foo {
    public String capitalize(String str) {
        return StringUtils.capitalize(str);
    }
}
"""
        }

        when:
        args('--parallel-threads=4')
        run('compileJava')

        then:
        noExceptionThrown()

        projectNames.each { projectName ->
            file("${projectName}/build/classes/main/Foo.class").exists()
        }
    }

    private static class RandomJdkPicker {
        private final List<JavaInfo> availableJdks
        private final Random random = new Random()

        RandomJdkPicker() {
            availableJdks = []
            determineJdksWithResolvableJavac()
            availableJdks.asImmutable()
        }

        private void determineJdksWithResolvableJavac() {
            AvailableJavaHomes.availableJdks.each { jdk ->
                try {
                    if(jdk.javacExecutable) {
                        availableJdks << jdk
                    }
                }
                catch(JavaHomeException e) {
                    // ignore
                }
            }
        }

        String getJavacExecutable() {
            TextUtil.escapeString(target.javacExecutable)
        }

        private JavaInfo getTarget() {
            int index = random.nextInt(availableJdks.size())
            availableJdks[index]
        }
    }
}
