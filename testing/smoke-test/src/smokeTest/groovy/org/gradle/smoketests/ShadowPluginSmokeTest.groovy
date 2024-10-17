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

package org.gradle.smoketests


import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ShadowPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Issue('https://plugins.gradle.org/plugin/com.gradleup.shadow')
    def 'shadow plugin'() {
        given:
        buildFile << """
            import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

            plugins {
                id 'java' // or 'groovy' Must be explicitly applied
                id 'com.gradleup.shadow' version '$TestedVersions.shadow'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'commons-collections:commons-collections:3.2.2'
            }

            shadowJar {
                transform(ServiceFileTransformer)

                manifest {
                    attributes 'Test-Entry': 'PASSED'
                }
            }
            """.stripIndent()


        when:

        def result = runner('shadowJar').build()

        then:
        result.task(':shadowJar').outcome == SUCCESS

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateStored()
        }

        when:
        runner('clean').build()
        result = runner('shadowJar').build()

        then:
        result.task(':shadowJar').outcome == SUCCESS

        and:
        if (GradleContextualExecuter.isConfigCache()) {
            result.assertConfigurationCacheStateLoaded()
        }
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'com.gradleup.shadow': Versions.of(TestedVersions.shadow)
        ]
    }


}
