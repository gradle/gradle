/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.Project
import org.gradle.features.binding.SchemaDefinition
import org.gradle.features.binding.SchemaProjectFeatureApplyAction
import org.gradle.features.binding.SchemaProjectTypeApplyAction
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import spock.lang.Specification

class PluginInspectorTest extends Specification {

    def ruleDetector = Mock(ModelRuleSourceDetector) {
        hasRules(_) >> false
    }
    def inspector = new PluginInspector(ruleDetector)

    def "recognizes a schema project type apply action as a project feature declaration"() {
        when:
        def potential = inspector.inspect(SchemaType)

        then:
        potential.type == PotentialPlugin.Type.PROJECT_FEATURE_DECLARATION_CLASS
        !potential.imperative
        !potential.hasRules
    }

    def "recognizes a schema project feature apply action as a project feature declaration"() {
        expect:
        inspector.inspect(SchemaFeature).type == PotentialPlugin.Type.PROJECT_FEATURE_DECLARATION_CLASS
    }

    def "recognizes an imperative plugin"() {
        expect:
        inspector.inspect(ImperativePlugin).type == PotentialPlugin.Type.IMPERATIVE_CLASS
    }

    def "treats a plain class as unknown"() {
        expect:
        inspector.inspect(String).type == PotentialPlugin.Type.UNKNOWN
    }

    interface MyDefinition extends SchemaDefinition {}

    static abstract class SchemaType implements SchemaProjectTypeApplyAction<MyDefinition> {
        @Override
        void apply(MyDefinition definition) {}
    }

    static abstract class SchemaFeature implements SchemaProjectFeatureApplyAction<MyDefinition, Object> {
        @Override
        void apply(MyDefinition definition, Object parent) {}
    }

    static class ImperativePlugin implements Plugin<Project> {
        @Override
        void apply(Project project) {}
    }
}
