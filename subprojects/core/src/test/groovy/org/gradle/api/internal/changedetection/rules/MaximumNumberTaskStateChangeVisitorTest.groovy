/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules

import spock.lang.Specification

class MaximumNumberTaskStateChangeVisitorTest extends Specification {

    def collectingVisitor = new CollectingTaskStateChangeVisitor()
    def visitor = new MaximumNumberTaskStateChangeVisitor(2, collectingVisitor)

    def "will not accept more changes than specified"() {
        def change1 = Mock(TaskStateChange)
        def change2 = Mock(TaskStateChange)
        def change3 = Mock(TaskStateChange)

        expect:
        visitor.visitChange(change1)
        !visitor.visitChange(change2)
        !visitor.visitChange(change3)
        collectingVisitor.changes == [change1, change2]
    }

}
