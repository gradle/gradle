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

package org.gradle.api.reporting.model

import spock.lang.Specification

class ModelReportNodeBuilderTest extends Specification {

    def "builds a report node structure from a DSL"() {
        ReportNode node = ModelReportNodeBuilder.fromDsl({
            model {
                childOne()
                childTwo(aValue: 'someThing', anotherValue: 'somethingElse')
            }
        }).get()

        expect:
        node.'**'.childOne
        node.'**'.childTwo.@aValue[0] == 'someThing'
        node.'**'.childTwo.@anotherValue[0] == 'somethingElse'
    }

    def "can accept an empty closure"() {
        def node = ModelReportNodeBuilder.fromDsl {}.get()
        expect:
        node == null
    }
}
