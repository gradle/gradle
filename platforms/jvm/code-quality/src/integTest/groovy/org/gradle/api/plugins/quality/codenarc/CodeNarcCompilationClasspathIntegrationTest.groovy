/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins.quality.codenarc

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class CodeNarcCompilationClasspathIntegrationTest extends AbstractIntegrationSpec {

    private final static String CONFIG_FILE_PATH = 'config/codenarc/rulesets.groovy'
    private final static String MIN_SUPPORTED_COMPILATION_CLASSPATH_VERSION = '0.27.0'
    private final static String UNSUPPORTED_COMPILATION_CLASSPATH_VERSION = '0.26.0'

    private static String supportedCompilationClasspathVersion() {
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
            return "1.6"
        }
        return MIN_SUPPORTED_COMPILATION_CLASSPATH_VERSION
    }

    def "compilation classpath can be specified for a CodeNarc task"() {
        given:
        buildFileWithCodeNarcAndCompilationClasspath(supportedCompilationClasspathVersion())
        cloneWithoutCloneableRuleEnabled()
        codeViolatingCloneWithoutCloneableRule()

        when:
        fails("codenarcMain")

        then:
        failure.assertHasCause('CodeNarc rule violations were found')
    }

    @Requires(UnitTestPreconditions.Jdk15OrEarlier)
    def "an informative error is shown when a compilation classpath is specified on a CodeNarc task when using an incompatible CodeNarc version"() {
        given:
        buildFileWithCodeNarcAndCompilationClasspath(UNSUPPORTED_COMPILATION_CLASSPATH_VERSION)
        cloneWithoutCloneableRuleEnabled()
        codeViolatingCloneWithoutCloneableRule()

        when:
        fails("codenarcMain")

        then:
        failure.assertHasCause("The compilationClasspath property of CodeNarc task can only be non-empty when using CodeNarc $MIN_SUPPORTED_COMPILATION_CLASSPATH_VERSION or newer.")
    }

    private void buildFileWithCodeNarcAndCompilationClasspath(String codeNarcVersion) {
        buildFile << """
            plugins {
                id("groovy")
                id("codenarc")
            }

            ${mavenCentralRepository()}

            codenarc {
                toolVersion = '$codeNarcVersion'
                codenarc.configFile = file('$CONFIG_FILE_PATH')
            }

            dependencies {
                implementation localGroovy()
            }

            codenarcMain {
                compilationClasspath = configurations.compileClasspath
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

    private void codeViolatingCloneWithoutCloneableRule() {
        file('src/main/groovy/ViolatingClass.groovy') << '''
            class ViolatingClass {
                ViolatingClass clone() {}
            }
        '''
    }
}
