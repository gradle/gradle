/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.xdcl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

/**
 * Smoke coverage for the {@code .gradle.xdcl} scripting language: proves the distribution under
 * test routes xdcl settings/build scripts natively and that script failures surface through the
 * Problems API with file:line:column coordinates. Broader functional coverage builds on this.
 */
class XdclScriptingSmokeIntegrationTest extends AbstractIntegrationSpec {

    def "routes settings.gradle.xdcl and binds include"() {
        given:
        file("app").createDir()
        xdclSettingsFile '''
            settings {
              include ["app"]
            }
        '''

        when:
        succeeds("projects")

        then:
        outputContains("Project ':app'")
    }

    def "an evaluation error fails the build as a located problem"() {
        given:
        enableProblemsApiCheck()
        xdclSettingsFile '''
            settings {
              includ ["app"]
            }
        '''

        when:
        fails("help")

        then:
        failure.assertHasDescription("${file('settings.gradle.xdcl')}:3:15")

        and:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:xdcl:xdcl-evaluation-error'
            contextualLabel.contains("includ")
        }
    }

    def "can apply settings plugin from included build"() {
        given:
        xdclSettingsFile '''
            settings {
                pluginManagement {
                  includedBuilds ["build-logic"]
                }
                plugins [
                  { id "local-settings-plugin" }
                ]
            }
        '''
        xdclFile 'build-logic/settings.gradle.xdcl', '''
            settings {}
        '''
        buildFile 'build-logic/build.gradle', '''
            plugins {
              id "java-gradle-plugin"
            }
            gradlePlugin {
              plugins {
                localSettingsPlugin {
                  id = "local-settings-plugin"
                  implementationClass = "my.LocalSettingsPlugin"
                }
              }
            }
        '''
        javaFile 'build-logic/src/main/java/my/LocalSettingsPlugin.java', """
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class LocalSettingsPlugin implements Plugin<Settings> {
                @Override public void apply(Settings target) {
                    System.out.println("LocalSettingsPlugin applied!");
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("LocalSettingsPlugin applied!")
    }

    TestFile xdclSettingsFile(String script) {
        file('settings.gradle.xdcl') << script
    }

    TestFile xdclFile(String path, String script) {
        file(path) << script
    }
}
