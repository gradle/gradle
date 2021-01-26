/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests


import spock.lang.Issue

class ErrorPronePluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Issue("https://github.com/gradle/gradle/issues/9897")
    def 'errorprone plugin'() {

        // TODO comment on https://github.com/gradle/gradle/commit/c45540059cef1e72254188c636e8ca68aba7a369#commitcomment-39777864

        given:
        buildFile << """
            plugins {
                id('java')
                id("net.ltgt.errorprone") version "${TestedVersions.errorProne}"
            }

            ${mavenCentralRepository()}

            if (JavaVersion.current().java8) {
                dependencies {
                    errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")
                }
            }

            dependencies {
                errorprone("com.google.errorprone:error_prone_core:2.3.3")
            }

            tasks.withType(JavaCompile).configureEach {
                options.fork = true
                options.errorprone {
                    check("DoubleBraceInitialization", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
                }
            }
        """
        file("src/main/java/Test.java") << """
            import java.util.HashSet;
            import java.util.Set;

            public class Test {

                public static void main(String[] args) {
                }

            }
        """

        when:
        def result = runner('compileJava').forwardOutput().build()

        then:
        expectNoDeprecationWarnings(result)
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'net.ltgt.errorprone': Versions.of(TestedVersions.errorProne)
        ]
    }
}
