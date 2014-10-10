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

import org.gradle.api.internal.project.ProjectInternal
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

class ProjectAppliedPluginsContainerTest extends Specification {

    def handler = Mock(MethodRuleDefinitionHandler)
    def inspector = new ModelRuleInspector([handler])
    def registry = Mock(ModelRegistry)
    def project = Mock(ProjectInternal)

    ProjectAppliedPluginsContainer container = new ProjectAppliedPluginsContainer(project, null, inspector)

    static class HasSource {
        @RuleSource
        static class Rules {
            @Model
            static Thing thing() { new Thing() }
        }
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
        container.apply(HasInvalidSource)

        then:
        thrown(InvalidModelRuleDeclarationException)
    }

    def "extracting rules"() {
        when:
        def spec = Stub(Spec) {
            isSatisfiedBy(_) >> { MethodRuleDefinition definition -> definition.getAnnotation(Model) != null }
        }
        handler.spec >> spec
        project.modelRegistry >> registry

        and:
        container.apply(HasSource)

        then:
        1 * handler.register({ it.methodName == "thing" }, registry, _ as RuleSourceDependencies)
    }
}
