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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class IsolatedAntBuilderMemoryLeakTest extends AbstractIntegrationSpec {
    private void goodCode(String groovyVersion, TestFile root = testDirectory) {
        root.file("src/main/groovy/org/gradle/Class1.groovy") << "package org.gradle; class Class1 { }"
        buildFile << """
            apply plugin: 'codenarc'

            allprojects {
                repositories {
                    jcenter()
                }
            }

            allprojects {
                apply plugin: 'groovy'

                dependencies {
                    compile $groovyVersion
                    codenarc('org.codenarc:CodeNarc:0.24.1') {
                        exclude group: 'org.codehaus.groovy'
                    }
                    codenarc $groovyVersion
                }
            }
        """
    }

    private void withCodenarc(TestFile root = testDirectory) {
        root.file("config/codenarc/rulesets.groovy") << """
            ruleset {
                ruleset('rulesets/naming.xml')
            }
        """
        buildFile << """
            allprojects {
                apply plugin: 'codenarc'

                codenarc {
                    configFile = file('config/codenarc/rulesets.groovy')
                }
            }
"""
    }

    @Unroll
    void 'CodeNarc does not fail with PermGen space error, Groovy #groovyVersion'() {
        given:
        goodCode(groovyVersion)
        withCodenarc()

        expect:
        succeeds 'check'

        where:
        groovyVersion << [
            'localGroovy()',
            "'org.codehaus.groovy:groovy-all:2.3.10'",
            "'org.codehaus.groovy:groovy-all:2.2.1'",
            "'org.codehaus.groovy:groovy-all:2.1.9'",
            "'org.codehaus.groovy:groovy-all:2.0.4'",
            "'org.codehaus.groovy:groovy-all:1.8.7'"] * 3
    }

}
