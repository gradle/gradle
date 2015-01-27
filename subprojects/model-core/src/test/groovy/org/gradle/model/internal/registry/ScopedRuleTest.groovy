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

package org.gradle.model.internal.registry

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.model.RuleSource
import org.gradle.model.collection.internal.HasDependencies
import org.gradle.model.internal.core.DependencyOnlyExtractedModelRule
import org.gradle.model.internal.core.ExtractedModelRule
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.inspect.AbstractAnnotationDrivenModelRuleExtractor
import org.gradle.model.internal.inspect.MethodModelRuleExtractors
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.ModelRuleExtractor
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class ScopedRuleTest extends Specification {

    def extractors = [new DependencyAddingModelRuleExtractor()] + MethodModelRuleExtractors.coreExtractors(DefaultModelSchemaStore.getInstance())
    def registry = new ModelRegistryHelper(new DefaultModelRegistry(new ModelRuleExtractor(extractors)))

    static class RuleSourceUsingRuleWithDependencies extends RuleSource {
        @HasDependencies
        void rule() {}
    }

    class ImperativePlugin implements Plugin<Project> {
        void apply(Project target) {
        }
    }

    class DependencyAddingModelRuleExtractor extends AbstractAnnotationDrivenModelRuleExtractor<HasDependencies> {
        @Override
        def <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition) {
            new DependencyOnlyExtractedModelRule([ModelType.of(ImperativePlugin)])
        }
    }

    def "cannot apply a scoped rule that has dependencies"() {
        registry.createInstance("values", "foo")
                .apply("values", RuleSourceUsingRuleWithDependencies)

        when:
        registry.get("values")

        then:
        ModelRuleExecutionException e = thrown()
        e.cause.class == IllegalStateException
        e.cause.message.startsWith "Rule source $RuleSourceUsingRuleWithDependencies cannot have plugin dependencies"
    }

}
