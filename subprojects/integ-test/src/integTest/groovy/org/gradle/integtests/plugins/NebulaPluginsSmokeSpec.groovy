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

package org.gradle.integtests.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class NebulaPluginsSmokeSpec extends AbstractIntegrationSpec {
    @Requires(TestPrecondition.JDK7_OR_LATER)
    def 'nebula recommender plugin'() {
        when:
        buildScript """
            plugins {
              id "nebula.dependency-recommender" version "3.3.0"
            }

            apply plugin: 'java'

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
        succeeds 'build'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
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
        succeeds 'groovydoc'
    }
}
