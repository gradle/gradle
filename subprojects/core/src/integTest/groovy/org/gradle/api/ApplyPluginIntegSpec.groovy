/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ApplyPluginIntegSpec extends AbstractIntegrationSpec {

    @Issue("GRADLE-2358")
    def "can reference plugin by id in unitest"() {

        given:
        file("src/main/groovy/org/acme/TestPlugin") << """class TestPlugin implements Plugin<Project>{
            apply(Project project){
                println "testplugin applied"

            }
        }"""

        file("src/main/resources/META-INF/gradle-plugins/testplugin.properties") << "implementation-class=org.acme.TestPlugin"

        file("src/test/groovy/org/acme/TestPluginSpec") << """class TestPluginSpec extends SpecificationPlugin{
            def "can apply plugin by id"(){
                Project project = ProjectBuilder.builder().build()
                project.apply(plugin:"testdplugin")
                assert project.plugins.withType(TestPlugin).size() == 1
            }
        }
        """
        buildFile << '''
            apply plugin: 'groovy'
        '''

        expect:
        succeeds("test")
    }
}