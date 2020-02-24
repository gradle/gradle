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

package org.gradle.jvm.test

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class AbstractJUnitTestExecutionIntegrationSpec extends AbstractIntegrationSpec {

    def setup() {
        expectDeprecationWarnings()
    }

    def expectDeprecationWarnings() {
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The java-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The junit-test-suite plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    protected void applyJUnitPlugin(boolean declareRepo = true) {
        buildFile << '''
            plugins {
                id 'jvm-component'
                id 'java-lang'
                id 'junit-test-suite'
            }
        '''
        if (declareRepo) {
            buildFile << jcenterRepository()
        }
    }

    protected void myTestSuiteSpec(String testedComponent=null) {
        buildFile << """
            model {
                testSuites {
                    myTest(JUnitTestSuiteSpec) {
                        jUnitVersion '4.12'
                        ${testedComponent?"testing \$.components.$testedComponent":''}
                    }
                }
            }
        """
    }
}
