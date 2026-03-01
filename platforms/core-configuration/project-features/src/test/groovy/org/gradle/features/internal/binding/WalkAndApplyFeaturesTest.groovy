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

package org.gradle.features.internal.binding

import org.gradle.api.internal.DynamicObjectAware
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.internal.extensibility.ExtensibleDynamicObject
import org.gradle.internal.metaobject.DynamicInvokeResult
import spock.lang.Specification

class WalkAndApplyFeaturesTest extends Specification {

    def "walkAndApplyFeatures applies features in depth-first order"() {
        // Root has two child features. The first child has a grandchild feature.
        // Verify depth-first: child1 → grandchild → child2.
        given:
        def applied = []
        def rootContext = buildDefinitionContext([
            "child1": mockFeatureApplication("child1", applied, [
                "grandchild": mockFeatureApplication("grandchild", applied, [:])
            ]),
            "child2": mockFeatureApplication("child2", applied, [:])
        ])
        def root = mockDefinitionWith(rootContext)

        when:
        ProjectFeatureSupportInternal.walkAndApplyFeatures(root)

        then:
        applied == ["child1", "grandchild", "child2"]
    }

    private ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext buildDefinitionContext(Map childFeaturesMap) {
        Mock(ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext) {
            _ * it.childFeatures() >> childFeaturesMap
            _ * it.nestedDefinitions() >> []
        }
    }

    private ProjectFeatureApplicator.FeatureApplication mockFeatureApplication(String name, List applied, Map children) {
        def childContext = buildDefinitionContext(children)
        def definition = mockDefinitionWith(childContext)
        Mock(ProjectFeatureApplicator.FeatureApplication) {
            _ * it.getDefinitionInstance() >> definition
            _ * it.apply() >> { applied << name }
        }
    }

    private def mockDefinitionWith(ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext context) {
        def dynamicObject = Mock(ExtensibleDynamicObject) {
            tryInvokeMethod(ProjectFeaturesDynamicObject.CONTEXT_METHOD_NAME, _ as Object[]) >> DynamicInvokeResult.found(context)
        }
        Mock(MockDefinition) {
            _ * it.asDynamicObject >> dynamicObject
        }
    }

    // Combined type so getDefinitionInstance() can return it without a cast failure
    private interface MockBuildModel extends BuildModel {}
    private interface MockDefinition extends Definition<MockBuildModel>, DynamicObjectAware {}
}
