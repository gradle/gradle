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