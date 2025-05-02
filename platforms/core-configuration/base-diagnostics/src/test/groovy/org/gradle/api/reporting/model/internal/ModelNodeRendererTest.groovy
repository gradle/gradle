/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.model.internal

import org.gradle.model.internal.core.ModelNode
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import spock.lang.Specification


class ModelNodeRendererTest extends Specification {

    def "should filter rules removing duplicates and excluding creators"() {
        ModelRuleDescriptor creator = Mock()
        creator.describeTo(_) >> { Appendable a ->
            a.append("creator")
        }

        ModelRuleDescriptor mutator = Mock()
        mutator.describeTo(_) >> { Appendable a ->
            a.append("mutator")
        }

        ModelNode modelNode = Mock()
        modelNode.getDescriptor() >> creator
        modelNode.getExecutedRules() >> [creator, mutator, creator, mutator]

        expect:
        ModelNodeRenderer.uniqueExecutedRulesExcludingCreator(modelNode).asList() == [mutator]
    }
}
