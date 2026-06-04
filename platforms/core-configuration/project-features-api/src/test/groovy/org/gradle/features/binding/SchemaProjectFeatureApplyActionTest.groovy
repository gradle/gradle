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

package org.gradle.features.binding

import spock.lang.Specification

class SchemaProjectFeatureApplyActionTest extends Specification {

    def definition = Mock(SchemaDefinition)
    def parent = new Object()

    def "delegates to the simplified apply with the definition and parent definition"() {
        given:
        def capturedDefinition = []
        def capturedParent = []
        def action = new SchemaProjectFeatureApplyAction<SchemaDefinition, Object>() {
            @Override
            void apply(SchemaDefinition d, Object p) {
                capturedDefinition << d
                capturedParent << p
            }
        }

        when:
        action.apply(null, definition, null, parent)

        then:
        capturedDefinition == [definition]
        capturedParent == [parent]
    }

    def "delegates when invoked through the ProjectFeatureApplyAction supertype reference"() {
        given:
        def capturedDefinition = []
        def capturedParent = []
        ProjectFeatureApplyAction<SchemaDefinition, BuildModel.None, Object> action = new SchemaProjectFeatureApplyAction<SchemaDefinition, Object>() {
            @Override
            void apply(SchemaDefinition d, Object p) {
                capturedDefinition << d
                capturedParent << p
            }
        }

        when:
        action.apply(null, definition, null, parent)

        then:
        capturedDefinition == [definition]
        capturedParent == [parent]
    }

    def "ignores the context and build model"() {
        given:
        def capturedDefinition = []
        def capturedParent = []
        def action = new SchemaProjectFeatureApplyAction<SchemaDefinition, Object>() {
            @Override
            void apply(SchemaDefinition d, Object p) {
                capturedDefinition << d
                capturedParent << p
            }
        }
        def context = Mock(ProjectFeatureApplicationContext)
        def buildModel = new BuildModel.None()

        when:
        action.apply(context, definition, buildModel, parent)

        then:
        capturedDefinition == [definition]
        capturedParent == [parent]
        0 * context._
    }
}
