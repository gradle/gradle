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

import groovy.transform.Immutable
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.internal.VersionNumber

class IsolatedAntBuilderMemoryLeakIntegrationTest extends AbstractIntegrationSpec {

    private void goodCode(String groovyVersion, TestFile root = testDirectory) {
        root.file("src/main/java/org/gradle/Class0.java") << "package org.gradle; public class Class0 { }"
        root.file("src/main/groovy/org/gradle/Class1.groovy") << "package org.gradle; class Class1 { }"
        buildFile << """

            allprojects {
                ${mavenCentralRepository()}
            }

            allprojects {
                apply plugin: 'groovy'

                dependencies {
                    implementation $groovyVersion
                }
            }
        """
    }

    private void withCodenarc(VersionPair versions, TestFile root = testDirectory) {
        root.file("config/codenarc/rulesets.groovy") << """
            ruleset {
                ruleset('rulesets/naming.xml')
            }
        """
        buildFile << """
            allprojects {
                apply plugin: 'codenarc'

                dependencies {
                    codenarc("org.codenarc:CodeNarc:${versions.codenarcVersion}") {
                        exclude group: 'org.apache.groovy'
                        exclude group: 'org.codehaus.groovy'
                    }
                    codenarc $versions.groovyVersion
                }

                codenarc {
                    configFile = file('config/codenarc/rulesets.groovy')
                }

            }
        """
    }

    private void withCheckstyle(TestFile root = testDirectory) {
        root.file("config/checkstyle/checkstyle.xml") << """<!DOCTYPE module PUBLIC
                    "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
                    "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
            <module name="Checker">
            </module>
        """
        buildFile << """
            allprojects {
                apply plugin: 'checkstyle'
            }
        """
    }

    void 'CodeNarc does not fail with PermGen space error'() {
        given:
        withCodenarc(versions)
        withCheckstyle()
        goodCode(versions.groovyVersion)

        expect:
        succeeds 'check'

        where:
        versions << groovyAndCodenarcVersions() * 3
    }

    @Immutable
    static class VersionPair {
        String groovyVersion
        String codenarcVersion
    }

    private static List<VersionPair> groovyAndCodenarcVersions() {
        def isAtLeastGroovy4 = VersionNumber.parse(GroovySystem.version).major >= 4

        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
            return [
                new VersionPair("localGroovy()", isAtLeastGroovy4 ? '3.1.0-groovy-4.0' : '3.1.0'),
                new VersionPair("'org.codehaus.groovy:groovy:3.0.5', 'org.codehaus.groovy:groovy-templates:3.0.5'", '3.1.0')
            ]
        }
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_9)) {
            return [
                new VersionPair("localGroovy()", isAtLeastGroovy4 ? '3.1.0-groovy-4.0' : '3.1.0'),
                // Leave this at 2.4.7 even if Groovy is upgraded
                new VersionPair("'org.codehaus.groovy:groovy-all:2.4.7'", '3.1.0')
            ]
        }
        return [
            new VersionPair("localGroovy()", isAtLeastGroovy4 ? '3.1.0-groovy-4.0' : '3.1.0'),
            //new VersionPair("'org.apache.groovy:groovy-all:4.0.5'", '3.1.0-groovy-4.0'), // FIXME cannot work due to leaking Groovy 3.0.12 jar
            new VersionPair("'org.codehaus.groovy:groovy-all:3.0.12'", '3.1.0'),
            new VersionPair("'org.codehaus.groovy:groovy-all:2.3.10'", '3.1.0'),
            new VersionPair("'org.codehaus.groovy:groovy-all:2.2.1'", '3.1.0'),
            new VersionPair("'org.codehaus.groovy:groovy-all:2.1.9'", '3.1.0'),
            new VersionPair("'org.codehaus.groovy:groovy-all:2.0.4'", '3.1.0'),
            new VersionPair("'org.codehaus.groovy:groovy-all:1.8.7'", '3.1.0')
        ]
    }

    @Requires(TestPrecondition.JDK11_OR_LATER) // grgit 5 requires JDK 11, see https://github.com/ajoberstar/grgit/issues/355
    void "does not fail with a PermGen space error or a missing method exception"() {
        given:
        initGitDir()
        buildFile << """
            buildscript {
              repositories {
                ${mavenCentralRepository()}
              }

              dependencies {
                classpath "org.ajoberstar.grgit:grgit-core:5.0.0"
              }
            }

            import org.ajoberstar.grgit.*
            Grgit.open(currentDir: project.rootProject.rootDir)
        """
        withCheckstyle()
        goodCode('localGroovy()')

        expect:
        succeeds 'check'

        where:
        iteration << (0..10)
    }
}
