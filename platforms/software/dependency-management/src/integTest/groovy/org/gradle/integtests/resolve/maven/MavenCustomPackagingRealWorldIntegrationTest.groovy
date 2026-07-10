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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MavenCustomPackagingRealWorldIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            ${mavenCentralRepository()}
        """.stripIndent()
    }

    def 'resolve maven module with non-existing custom packaging artifact - does not apply java plugin'() {
        given:
        buildFile << """
            configurations {
                conf
            }
            dependencies {
                // this POM declares packaging of 'hk2-jar', but the artifact is 'jar'
                // see https://search.maven.org/artifact/org.glassfish.ha/ha-api/3.1.7/hk2-jar
                conf 'org.glassfish.ha:ha-api:3.1.7'
            }

            tasks.register('resolve', Sync) {
                into 'libs'
                from configurations.conf
            }
        """.stripIndent()

        when:
        succeeds('resolve')

        then:
        file('libs').assertHasDescendants('ha-api-3.1.7.jar')
    }

    def 'resolve maven module with non-existing custom packaging artifact - applies java plugin'() {
        given:
        buildFile.text = '''plugins {
            id 'java'
        }
        '''.stripIndent() + buildFile.text

        buildFile << """
            dependencies {
                // this POM declares packaging of 'hk2-jar', but the artifact is 'jar'
                // see https://search.maven.org/artifact/org.glassfish.ha/ha-api/3.1.7/hk2-jar
                implementation 'org.glassfish.ha:ha-api:3.1.7'
            }

            tasks.register('resolve', Sync) {
                into 'libs'
                from configurations.compileClasspath
            }
        """.stripIndent()

        when:
        succeeds('resolve')

        then:
        file('libs').assertHasDescendants('ha-api-3.1.7.jar')
    }

    def 'resolve maven module with valid custom packaging artifact - does not apply java plugin'() {
        given:
        buildFile << """
            configurations {
                conf
            }
            dependencies {
                // this POM declares packaging of 'nar', and the artifact is 'nar'
                // see https://search.maven.org/artifact/org.apache.nifi/nifi-smb-nar/1.12.0/nar
                conf 'org.apache.nifi:nifi-smb-nar:1.12.0'
            }

            tasks.register('resolve', Sync) {
                into 'libs'
                from configurations.conf
            }
        """.stripIndent()

        when:
        succeeds('resolve')

        then:
        file('libs').assertContainsDescendants('nifi-smb-nar-1.12.0.nar')
    }

    def 'resolve maven module with valid custom packaging artifact - applies java plugin'() {
        given:
        buildFile.text = '''plugins {
            id 'java'
        }
        '''.stripIndent() + buildFile.text

        buildFile << """
            dependencies {
                // this POM declares packaging of 'nar', and the artifact is 'nar'
                // see https://search.maven.org/artifact/org.apache.nifi/nifi-smb-nar/1.12.0/nar
                implementation 'org.apache.nifi:nifi-smb-nar:1.12.0'
            }

            tasks.register('resolve', Sync) {
                into 'libs'
                from configurations.compileClasspath
            }
        """.stripIndent()

        when:
        succeeds('resolve')

        then:
        file('libs').assertContainsDescendants('nifi-smb-nar-1.12.0.nar')
    }
}
