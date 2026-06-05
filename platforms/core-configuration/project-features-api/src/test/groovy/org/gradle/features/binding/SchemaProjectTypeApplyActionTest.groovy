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

class SchemaProjectTypeApplyActionTest extends Specification {

    def definition = Mock(SchemaDefinition)

    def "delegates to the simplified apply with the definition"() {
        given:
        def captured = []
        def action = new SchemaProjectTypeApplyAction<SchemaDefinition>() {
            @Override
            void apply(SchemaDefinition d) {
                captured << d
            }
        }

        when:
        action.apply(null, definition, null)

        then:
        captured == [definition]
    }

    def "delegates when invoked through the ProjectTypeApplyAction supertype reference"() {
        given:
        def captured = []
        ProjectTypeApplyAction<SchemaDefinition, BuildModel.None> action = new SchemaProjectTypeApplyAction<SchemaDefinition>() {
            @Override
            void apply(SchemaDefinition d) {
                captured << d
            }
        }

        when:
        action.apply(null, definition, null)

        then:
        captured == [definition]
    }

    def "ignores the context and build model"() {
        given:
        def captured = []
        def action = new SchemaProjectTypeApplyAction<SchemaDefinition>() {
            @Override
            void apply(SchemaDefinition d) {
                captured << d
            }
        }
        def context = Mock(ProjectFeatureApplicationContext)
        def buildModel = new BuildModel.None()

        when:
        action.apply(context, definition, buildModel)

        then:
        captured == [definition]
        0 * context._
    }
}
