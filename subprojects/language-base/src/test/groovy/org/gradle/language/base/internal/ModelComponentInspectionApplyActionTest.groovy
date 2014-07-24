/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.base.internal

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.plugins.PluginApplication
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.runtime.base.ComponentModel
import org.gradle.runtime.base.InvalidComponentModelException
import org.gradle.runtime.base.LibrarySpec
import org.gradle.runtime.base.internal.registry.ComponentModelInspectionApplyAction
import org.gradle.runtime.base.library.DefaultLibrarySpec
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class ModelComponentInspectionApplyActionTest extends Specification {
    Instantiator instantiator = new DirectInstantiator()
    PluginApplication pluginApplication = Mock()
    Project project
    ComponentModelInspectionApplyAction componentModelInspector

    def "applys ComponentModelBasePlugin if needed"() {
        given:
        aProjectPlugin(plugin)
        and:
        when:
        componentModelInspector.execute(pluginApplication)
        then:
        project.plugins.hasPlugin(ComponentModelBasePlugin) == applied
        where:
        plugin                | applied
        new EmptyTestPlugin() | false
        new ValidTestPlugin() | true
    }


    @Unroll
    def "decent error message for #descr"() {
        given:
        aProjectPlugin(plugin)
        when:
        componentModelInspector.execute(pluginApplication)
        then:
        def ex = thrown(InvalidComponentModelException)
        ex.message == expectedMessage

        where:
        plugin                   | expectedMessage                                                          | descr
        new Invalid1TestPlugin() | "Parameter 'implementation' not declared in ComponentModel declaration." | "missing implementation parameter"
        new InvalidTest2Plugin() | "Parameter 'type' not declared in ComponentModel declaration."           | "missing type parameter"
        new InvalidTest3Plugin() | "Invalid 'type' parameter for ComponentModel declaration."               | "invalid type parameter"
        new InvalidTest4Plugin() | "Invalid 'implementation' parameter for ComponentModel declaration."     | "invalid implementation parameter"
    }

    def "decent error when ComponentModel declared in non project plugin"() {
        given:
        aSettingsPlugin(new SettingsTestPlugin())
        when:
        componentModelInspector.execute(pluginApplication)
        then:
        def ex = thrown(InvalidComponentModelException)
        ex.message == "ComponentModel can only be declared for project plugins."
    }

    def aProjectPlugin(def plugin) {
        project = ProjectBuilder.builder().build()
        _ * pluginApplication.target >> project
        1 * pluginApplication.plugin >> plugin
        componentModelInspector = new ComponentModelInspectionApplyAction(instantiator)
    }

    def aSettingsPlugin(def plugin) {
        Settings settings = Mock(Settings)
        _ * pluginApplication.target >> settings
        _ * pluginApplication.plugin >> plugin
        componentModelInspector = new ComponentModelInspectionApplyAction(instantiator)
    }


    interface SomeLibrarySpec extends LibrarySpec {}

    static class SomeLibrarySpecImpl extends DefaultLibrarySpec implements SomeLibrarySpec {}

    class EmptyTestPlugin implements Plugin<Project> {
        @Override
        void apply(Project target) {}
    }

    class ValidTestPlugin implements Plugin<Project> {
        @Override
        void apply(Project target) {
        }

        @ComponentModel(type = SomeLibrarySpec, implementation = SomeLibrarySpecImpl)
        static class SomeStaticSubClass {
        }
    }

    class Invalid1TestPlugin implements Plugin<Project> {
        @Override
        void apply(Project target) {}

        @ComponentModel(type = SomeLibrarySpec)
        static class SomeStaticSubclass {
        }
    }

    class InvalidTest2Plugin implements Plugin<Project> {
        @Override
        void apply(Project target) {
        }

        @ComponentModel(implementation = SomeLibrarySpecImpl)
        static class SomeStaticSubClass {
        }
    }

    class InvalidTest3Plugin implements Plugin<Project> {
        @Override
        void apply(Project target) {
        }

        @ComponentModel(type = SomeInvalidTestLib, implementation = SomeLibrarySpecImpl)
        static class SomeStaticSubClass {
        }
    }

    class InvalidTest4Plugin implements Plugin<Project> {
        @Override
        void apply(Project target) {
        }

        @ComponentModel(type = SomeLibrarySpec, implementation = SomeInvalidTestLibImpl)
        static class SomeStaticSubClass {
        }

    }

    class SettingsTestPlugin implements Plugin<Settings> {
        @Override
        void apply(Settings target) {}

        @ComponentModel(type = SomeLibrarySpec, implementation = SomeLibrarySpecImpl)
        static class SomeStaticSubclass {
        }
    }

    interface SomeInvalidTestLib {}

    class SomeInvalidTestLibImpl {}
}


