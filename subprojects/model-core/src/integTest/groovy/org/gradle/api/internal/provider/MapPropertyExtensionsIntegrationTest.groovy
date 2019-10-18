/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import spock.lang.Issue

class MapPropertyExtensionsIntegrationTest extends AbstractPluginIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/11028")
    def "MapProperty retains extension module methods after JAR entry renamed to META-INF/groovy"() {
        given:
        buildFile << '''
            def prop = project.objects.mapProperty(String, String)
            
            task verify {
                inputs.property('prop', prop)
                prop = ['key1':'value1']
                doLast {
                    assert prop['key1'] == 'value1' 
                    println "prop = ${prop}"
                }
            }
            '''

        when:
        run('verify')

        then:
        outputContains("value1")
    }
}
