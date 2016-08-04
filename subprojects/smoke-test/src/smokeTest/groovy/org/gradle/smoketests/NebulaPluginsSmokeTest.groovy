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

import org.junit.Assume

class NebulaPluginsSmokeTest extends AbstractSmokeTest {

    def 'nebula recommender plugin'() {
        when:
        buildFile << """
            plugins {
                id "java"
                id "nebula.dependency-recommender" version "3.3.0"
            }

            repositories {
                jcenter()
            }

            dependencyRecommendations {
                mavenBom module: 'netflix:platform:latest.release'
            }

            dependencies {
                compile 'com.google.guava:guava' // no version, version is recommended
                compile 'commons-lang:commons-lang:2.6' // I know what I want, don't recommend
            }
            """

        then:
        runner('build').build()
    }

    def 'nebula plugin plugin'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.plugin-plugin' version '4.15.0'
            }
        """

        file("src/main/groovy/pkg/Thing.java") << """
            package pkg;

            import java.util.ArrayList;
            import java.util.List;

            public class Thing {
                private List<String> firstOrderDepsWithoutVersions = new ArrayList<>();
            }
        """

        then:
        runner('groovydoc').build()
    }

    def 'nebula lint plugin'() {
        when:
        buildFile << """
            plugins {
                id "nebula.lint" version "0.30.9"
            }
        """.stripIndent()

        then:
        def result = runner('buildEnvironment', 'lintGradle').buildAndFail()

        result.output.contains('''Caused by: java.lang.NoClassDefFoundError: org/gradle/logging/StyledTextOutput
\tat com.netflix.nebula.lint.plugin.FixGradleLintTask$1.$getStaticMetaClass(FixGradleLintTask.groovy)''')

        Assume.assumeTrue("The nebula lint plugin is broken, it depends on internal StyledTextOutput", false)
    }

    def 'nebula dependency lock plugin'() {
        when:
        buildFile << """
            plugins {
                id "nebula.dependency-lock" version "4.3.0"
            }
        """.stripIndent()

        then:
        runner('buildEnvironment', 'generateLock').build()
    }
}
