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

import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ShadowPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Issue('https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow')
    def 'shadow plugin #version'() {
        given:
        buildFile << """
            import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

            plugins {
                id 'java' // or 'groovy' Must be explicitly applied
                id 'com.github.johnrengelman.shadow' version '$version'
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
        assertConfigurationCacheStateStored()

        when:
        runner('clean').build()
        result = runner('shadowJar').build()

        then:
        result.task(':shadowJar').outcome == SUCCESS
        assertConfigurationCacheStateLoaded()

        where:
        version << TestedVersions.shadow
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'com.github.johnrengelman.shadow': TestedVersions.shadow
        ]
    }
}
