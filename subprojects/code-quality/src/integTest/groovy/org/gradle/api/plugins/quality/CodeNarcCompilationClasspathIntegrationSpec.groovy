/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.startsWith

class CodeNarcCompilationClasspathIntegrationSpec extends AbstractIntegrationSpec {

    private final static String CONFIG_FILE_PATH = 'config/codenarc/rulesets.groovy'

    def "compilation classpath can be specified for a CodeNarc task"() {
        given:
        buildFileWithCodeNarcAndCompilationClasspath('0.27.0')
        cloneWithoutCloneableRuleEnabled()
        codeViolatingCloneWithoutCloneableRule()

        expect:
        fails("codenarcMain")
        failure.assertThatCause(startsWith("CodeNarc rule violations were found"))
    }

    def "an informative error is shown when a compilation classpath is specified on a CodeNarc task when using an incompatible CodeNarc version"() {
        given:
        buildFileWithCodeNarcAndCompilationClasspath('0.26.0')
        cloneWithoutCloneableRuleEnabled()
        codeViolatingCloneWithoutCloneableRule()

        expect:
        fails("codenarcMain")
        failure.assertThatCause(equalTo("The compilationClasspath property of CodeNarc task can only be non-empty when using CodeNarc 0.27.0 or newer."))
    }

    private void buildFileWithCodeNarcAndCompilationClasspath(String codeNarcVersion) {
        buildFile << """
            apply plugin: "codenarc"
            apply plugin: "groovy"

            repositories {
                mavenCentral()
            }

            codenarc {
                toolVersion = '$codeNarcVersion'
                codenarc.configFile = file('$CONFIG_FILE_PATH') 
            }
            
            dependencies {
                compile localGroovy()
            }
            
            codenarcMain {
                compilationClasspath = configurations.compile
            }
        """
    }

    private void cloneWithoutCloneableRuleEnabled() {
        file(CONFIG_FILE_PATH) << '''
            ruleset {
                CloneWithoutCloneable
            }
        '''
    }

    private codeViolatingCloneWithoutCloneableRule() {
        file('src/main/groovy/ViolatingClass.groovy') << '''
            class ViolatingClass extends Tuple {
                ViolatingClass clone() {}
            }
        '''
    }
}
