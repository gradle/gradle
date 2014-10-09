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

package org.gradle.api.internal.plugins

import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.specs.Spec
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.Model
import org.gradle.model.RuleSource
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinitionHandler
import org.gradle.model.internal.inspect.ModelRuleInspector
import org.gradle.model.internal.inspect.RuleSourceDependencies
import org.gradle.model.internal.registry.ModelRegistry
import spock.lang.Specification
import spock.lang.Unroll

class ProjectAppliedPluginsTest extends Specification {

    def handler = Mock(MethodRuleDefinitionHandler)
    def inspector = new ModelRuleInspector([handler])
    def project = Mock(ProjectInternal)
    def pluginRegistry = Mock(PluginRegistry)
    def pluginContainer = Mock(PluginContainer)
    def registry = Mock(ModelRegistry)

    def spec = Stub(Spec) {
        isSatisfiedBy(_) >> { MethodRuleDefinition definition -> definition.getAnnotation(Model) != null }
    }

    def appliedPlugins = new ProjectAppliedPlugins(project, pluginRegistry, inspector)

    static class Thing {}

    static class HasSource {
        @RuleSource
        static class Rules {
            @Model
            static Thing thing() { new Thing() }
        }
    }

    @RuleSource
    static class PluginWithSources implements Plugin<Gradle> {
        void apply(Gradle target) {
        }

        @Model
        static Thing thing() { new Thing() }
    }

    static class PluginWithoutSources implements Plugin<Gradle> {
        void apply(Gradle target) {
        }
    }

    @Unroll
    def "applying #scenario class"() {
        when:
        handler.spec >> spec
        project.plugins >> pluginContainer
        project.modelRegistry >> registry

        and:
        appliedPlugins.apply(pluginClass)

        then:
        modelRegistrations * handler.register({ it.methodName == "thing" }, registry, _ as RuleSourceDependencies)

        and:
        pluginContainerApplications * pluginContainer.apply(pluginClass)

        where:
        scenario                   | pluginClass          | modelRegistration | pluginContainerApplication
        "rule source only"         | HasSource            | true              | false
        "plugin"                   | PluginWithoutSources | false             | true
        "plugin with rule sources" | PluginWithSources    | false             | true

        modelRegistrations = modelRegistration ? 1 : 0
        pluginContainerApplications = pluginContainerApplication ? 1 : 0
    }

    @Unroll
    def "applying #scenario class by id"() {
        when:
        handler.spec >> spec
        project.plugins >> pluginContainer
        project.modelRegistry >> registry
        pluginRegistry.getTypeForId(pluginClass.name) >> pluginClass

        and:
        appliedPlugins.apply(pluginClass.name)

        then:
        modelRegistrations * handler.register({ it.methodName == "thing" }, registry, _ as RuleSourceDependencies)

        and:
        pluginContainerApplications * pluginContainer.apply(pluginClass)

        where:
        scenario                   | pluginClass          | modelRegistration | pluginContainerApplication
        "rule source only"         | HasSource            | true              | false
        "plugin"                   | PluginWithoutSources | false             | true
        "plugin with rule sources" | PluginWithSources    | false             | true

        modelRegistrations = modelRegistration ? 1 : 0
        pluginContainerApplications = pluginContainerApplication ? 1 : 0
    }

    static class HasInvalidSource {
        @RuleSource
        class Rules {
            @Model
            static Thing thing() { new Thing() }
        }

    }

    def "error extracting rules"() {
        when:
        appliedPlugins.apply(HasInvalidSource)

        then:
        thrown(InvalidModelRuleDeclarationException)
    }

    def "error extracting rules for rule only classes applied by id"() {
        when:
        pluginRegistry.getTypeForId("has-invalid-source") >> HasInvalidSource

        and:
        appliedPlugins.apply("has-invalid-source")

        then:
        thrown(InvalidModelRuleDeclarationException)
    }

    def "extracting rules"() {
        when:
        handler.spec >> spec
        project.modelRegistry >> registry

        and:
        appliedPlugins.extractModelRulesAndAdd(HasSource)

        then:
        1 * handler.register({ it.methodName == "thing" }, registry, _ as RuleSourceDependencies)
    }
}
