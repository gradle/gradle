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

public class DeferredConfigurableExtensionIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        file('buildSrc/src/main/java/CustomPlugin.java') << """
import org.gradle.api.Project;
import org.gradle.api.Plugin;
public class CustomPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getExtensions().create("custom", CustomExtension.class);
    }
}
        """

        file('buildSrc/src/main/java/CustomExtension.java') << """
@org.gradle.api.plugins.DeferredConfigurable
public class CustomExtension {
    private final StringBuilder builder = new StringBuilder();
    public void append(String value) {
        builder.append(value);
    }

    public String getString() {
        return builder.toString();
    }
}
        """
    }

    def "configure actions on deferred configurable extension are deferred until access"() {
        when:
        buildFile << '''
apply plugin: CustomPlugin

version = "1"
custom {
    append project.version
}

version = "2"
custom {
    append project.version
}

assert custom.string == "22"
task test
'''
        then:
        succeeds('test')
    }

    def "cannot configure deferred configurable extension after access"() {
        when:
        settingsFile << '''rootProject.name = "deferred"'''
        buildFile << '''
apply plugin: CustomPlugin

version = "1"
custom {
    append project.version
}

assert custom.string == "1"

custom {
    append project.version
}
task test
'''
        then:
        fails('test')
        failure.assertHasDescription "A problem occurred evaluating root project 'deferred'"
        failure.assertHasCause "Cannot configure the 'custom' extension after it has been accessed."
    }
}
