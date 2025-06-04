/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.quality

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GitUtility
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

class AntWorkerMemoryLeakIntegrationTest extends AbstractIntegrationSpec {
    public static final String LOCAL_GROOVY = 'localGroovy()'

    private static String getGroovyDependencyAdditionScript(String groovyVersion, String configuration) {
        if (groovyVersion == LOCAL_GROOVY) {
            return "$configuration ${LOCAL_GROOVY}"
        } else {
            def groovyVersionNumber = VersionNumber.parse(groovyVersion)
            if (groovyVersionNumber.major <= 2) {
                return "$configuration 'org.codehaus.groovy:groovy-all:$groovyVersion'"
            }
            def group = groovyVersionNumber.major >= 4 ? 'org.apache.groovy' : 'org.codehaus.groovy'
            return """
                $configuration '$group:groovy:${groovyVersion}'
                $configuration '$group:groovy-templates:${groovyVersion}'
            """
        }
    }

    private void goodCode(String groovyVersion, TestFile root = testDirectory) {
        root.file("src/main/java/org/gradle/Class0.java") << "package org.gradle; public class Class0 { }"
        root.file("src/main/groovy/org/gradle/Class1.groovy") << "package org.gradle; class Class1 { }"
        root.file('build.gradle') << """

            allprojects {
                ${mavenCentralRepository()}
            }

            allprojects {
                apply plugin: 'groovy'

                dependencies {
                    ${getGroovyDependencyAdditionScript(groovyVersion, "implementation")}
                }
            }
        """
    }

    private void withCodenarc(String groovyVersion, TestFile root = testDirectory) {
        root.file("config/codenarc/rulesets.groovy") << """
            ruleset {
                ruleset('rulesets/naming.xml')
            }
        """
        def groovyVersionNumber = VersionNumber.parse(
            groovyVersion == LOCAL_GROOVY ? GroovySystem.version : groovyVersion
        )
        def codenarcVersion = switch (groovyVersionNumber.major) {
            case 1, 2 -> '0.24.1'
            default -> "3.6.0" + switch (groovyVersionNumber.major) {
                case 3 -> ""
                // Temporary override as there is no CodeNarc release for Groovy 5.0 yet
                case 5 -> "-groovy-4.0"
                default -> "-groovy-${groovyVersionNumber.major}.${groovyVersionNumber.minor}"
            }
        }
        root.file('build.gradle') << """
            allprojects {
                apply plugin: 'codenarc'

                dependencies {
                    codenarc('org.codenarc:CodeNarc:$codenarcVersion') {
                        exclude group: 'org.apache.groovy'
                        exclude group: 'org.codehaus.groovy'
                    }
                    ${getGroovyDependencyAdditionScript(groovyVersion, "codenarc")}
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
        root.file('build.gradle') << """
            allprojects {
                apply plugin: 'checkstyle'
            }
        """
    }

    @Issue('https://github.com/gradle/gradle/issues/22172')
    void 'CodeNarc/Checkstyle do not fail with PermGen space error'() {
        given:
        withCheckstyle()
        20.times { count ->
            def projectName = "project${count}"
            def projectDir = file(projectName).createDir()
            settingsFile << """
                include (':${projectName}')
            """
            withCodenarc(groovyVersion, projectDir)
            withCheckstyle(projectDir)
            goodCode(groovyVersion, projectDir)
        }

        expect:
        args('--max-workers=1')
        succeeds 'check'

        where:
        groovyVersion << groovyVersions()
    }

    private static Set<String> groovyVersions() {
        // Codenarc is not compatible with earlier Groovy versions
        VersionNumber lowerBound = VersionNumber.parse('1.8.8')

        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
            // Codenarc is not compatible with earlier Groovy versions on JDK11+
            lowerBound = VersionNumber.parse('2.1.9')
        }

        return [ LOCAL_GROOVY ] +
            // Leave this at 2.4.7 even if Groovy is upgraded (there is a known problem with 2.4.7 we want to be sure to test)
            GROOVY_2_4_7ifSupported +
            GroovyCoverage.SUPPORTED_BY_JDK.findAll {
                VersionNumber.parse(it) >= lowerBound
            }
    }

    private static Set<String> getGROOVY_2_4_7ifSupported() {
        return VersionNumber.parse(GroovyCoverage.SUPPORTED_BY_JDK.min()) <= VersionNumber.parse("2.4.7") ? [ "2.4.7" ] : []
    }

    @Requires([UnitTestPreconditions.Jdk11OrLater, IntegTestPreconditions.NotConfigCached]) // grgit 5 requires JDK 11, see https://github.com/ajoberstar/grgit/issues/355
    void "does not fail with a PermGen space error or a missing method exception"() {
        given:
        GitUtility.initGitDir(testDirectory)
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
        goodCode(LOCAL_GROOVY)

        expect:
        succeeds 'check'

        where:
        iteration << (0..10)
    }
}
