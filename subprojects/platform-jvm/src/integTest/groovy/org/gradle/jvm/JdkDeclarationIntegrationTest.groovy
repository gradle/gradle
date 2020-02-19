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
package org.gradle.jvm

import groovy.json.StringEscapeUtils
import org.gradle.api.JavaVersion
import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.Jvm
import spock.lang.Unroll

@UnsupportedWithInstantExecution(because = "software model")
class JdkDeclarationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id 'jvm-component'
            }
        """
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    //@Requires(TestPrecondition.NOT_WINDOWS)
    def "can declare an installed JDK and model report shows the resolved installed JDK"() {
        given:
        def jdks = discoveredJavaInstalls().indexed().collect { i, jdk ->
            """
                    jdk$i(LocalJava) {
                        path '${StringEscapeUtils.escapeJava(jdk.javaHome.toString())}'
                    }
            """
        }.join('')
        buildFile << """
            model {
                javaInstallations {
                    $jdks
                }
            }
        """

        when:
        succeeds 'model', '--format=short', '--showHidden'

        then:
        def report = ModelReportOutput.from(output)
        // for each declared JDK, there must be *at least* one installed JDK which Java Home corresponds
        // to the one declared. There may be less because they are deduplicated
        discoveredJavaInstalls().eachWithIndex { jdk, i ->
            assert report.modelNode.javaToolChains.'**'.@javaHome.any {
                it == jdk.javaHome.canonicalFile.absolutePath
            }
        }
    }

    private List<JavaInfo> discoveredJavaInstalls() {
        AvailableJavaHomes.availableJvms.unique(false) { it.javaHome.canonicalPath }
    }

    def "pointing to a non existent installation doesn't resolve to a JDK"() {
        given:
        buildFile << '''
            model {
                javaInstallations {
                    myJDK(LocalJava) {
                        path 'no-luck'
                    }
                }
            }
        '''

        when:
        fails 'model'

        then:
        failure.assertHasCause "Path to JDK 'myJDK' doesn't exist"
    }

    @Unroll
    def "pointing to an existent file or directory but not a JDK home throws reasonable error message"() {
        given:
        buildFile << """
            model {
                javaInstallations {
                    myJDK(LocalJava) {
                        path '$path'
                    }
                }
            }
        """

        when:
        fails 'model'

        then:
        failure.assertHasCause "JDK 'myJDK' is not a valid JDK installation"

        where:
        path << ['.', 'build.gradle']
    }

    def "Current Gradle JDK appears in installations"() {
        given:
        buildFile << '''
            model {
                javaInstallations {
                }
            }
        '''

        when:
        succeeds 'model', '--format=short', '--showHidden'

        then:
        def report = ModelReportOutput.from(output)
        // only uses the JDK that Gradle runs on
        assert report.modelNode.javaToolChains[0].children().size() == 1
        assert report.modelNode.javaToolChains.currentGradleJDK.@javaVersion == [JavaVersion.current().toString()]
    }

    def "Cannot declare the same JDK twice"() {
        given:
        def home = Jvm.current().javaHome.absolutePath
        buildFile << """
            model {
                javaInstallations {
                    openJdk(LocalJava) {
                        path "${StringEscapeUtils.escapeJava(home)}"
                    }
                    superJdk(LocalJava) {
                        path "${StringEscapeUtils.escapeJava(home)}"
                    }
                }
            }
        """

        when:
        fails 'model'

        then:
        failure.assertThatCause(containsNormalizedString('''Duplicate Java installation found:
   - 'openJdk', 'superJdk' are both pointing to the same JDK installation path:'''))

    }
}
