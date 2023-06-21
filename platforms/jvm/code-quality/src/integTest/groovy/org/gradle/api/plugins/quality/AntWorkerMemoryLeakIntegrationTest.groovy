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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

class AntWorkerMemoryLeakIntegrationTest extends AbstractIntegrationSpec {
    public static final String LOCAL_GROOVY = 'localGroovy()'

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
                    implementation $groovyVersion
                }
            }
        """
    }

    private void withCodenarc(String groovyVersion, String codenarcVersion = '0.24.1', TestFile root = testDirectory) {
        root.file("config/codenarc/rulesets.groovy") << """
            ruleset {
                ruleset('rulesets/naming.xml')
            }
        """
        root.file('build.gradle') << """
            allprojects {
                apply plugin: 'codenarc'

                dependencies {
                    codenarc('org.codenarc:CodeNarc:$codenarcVersion') {
                        exclude group: 'org.codehaus.groovy'
                    }
                    codenarc $groovyVersion
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
            withCodenarc(getDependencyFor(groovyVersion), getCodeNarcVesionFor(groovyVersion), projectDir)
            withCheckstyle(projectDir)
            goodCode(getDependencyFor(groovyVersion), projectDir)
        }

        expect:
        args('--max-workers=1')
        succeeds 'check'

        where:
        groovyVersion << groovyVersions()
    }

    private static String getCodeNarcVesionFor(String groovyVersion) {
        return VersionNumber.parse(groovyVersion).major >= 4 ? '3.1.0-groovy-4.0' : '0.24.1'
    }

    private static String getDependencyFor(String groovyVersion) {
        if (groovyVersion == LOCAL_GROOVY) {
            return LOCAL_GROOVY
        }

        def groovyVersionNumber = VersionNumber.parse(groovyVersion)
        String group = groovyVersionNumber.major >= 4 ? "org.apache.groovy" : "org.codehaus.groovy"
        String module = groovyVersionNumber.major >= 3 ? "groovy" : "groovy-all"
        String dependency = "'${group}:${module}:${groovyVersion}'"
        return groovyVersionNumber.major >= 3 ? dependency + ", '${group}:groovy-templates:${groovyVersion}'" : dependency
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

    @Requires(UnitTestPreconditions.Jdk11OrLater) // grgit 5 requires JDK 11, see https://github.com/ajoberstar/grgit/issues/355
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
        goodCode(LOCAL_GROOVY)

        expect:
        succeeds 'check'

        where:
        iteration << (0..10)
    }
}
