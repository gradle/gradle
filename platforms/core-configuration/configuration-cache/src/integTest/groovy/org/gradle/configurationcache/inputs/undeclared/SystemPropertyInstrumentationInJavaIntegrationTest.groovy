/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.inputs.undeclared


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest

class SystemPropertyInstrumentationInJavaIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "#method is instrumented in Java"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        file("buildSrc/src/main/java/SomePlugin.java") << """
            import ${Plugin.name};
            import ${Project.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    Object returned = $method;
                    System.out.println("returned = " + returned);
                }
            }
        """

        buildScript("""
            apply plugin: SomePlugin
        """)

        when:
        configurationCacheRun("-Dsome.property=some.value")

        then:
        configurationCache.assertStateStored()
        outputContains("returned = some.value")
        problems.assertResultHasProblems(result) {
            withInput("Plugin class 'SomePlugin': system property 'some.property'")
        }

        where:
        method                                                     | _
        "System.getProperties().get(\"some.property\")"            | _
        "System.getProperty(\"some.property\")"                    | _
        "System.getProperty(\"some.property\", \"default.value\")" | _
        "System.setProperty(\"some.property\", \"new.value\")"     | _
        "System.clearProperty(\"some.property\")"                  | _
    }

    def "setProperties is instrumented in Java"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        file("buildSrc/src/main/java/SomePlugin.java") << """
            import ${Plugin.name};
            import ${Project.name};
            import ${Properties.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    Properties newProps = new Properties();
                    System.getProperties().forEach(newProps::put);
                    newProps.replace("some.property", "new.value");
                    System.setProperties(newProps);
                }
            }
        """

        buildScript("""
            apply plugin: SomePlugin

             tasks.register("printProperty") {
                doLast {
                    println("returned = \${System.getProperty("some.property")}")
                }
            }
        """)

        when:
        configurationCacheRun("-Dsome.property=some.value", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("returned = new.value")

        when:
        configurationCacheRun("-Dsome.property=some.value", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("returned = new.value")
    }
}
