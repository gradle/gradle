/*
 * Copyright 2013 the original author or authors.
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

import spock.lang.Specification;

public class SimpleTaskStateChangesTest extends Specification {
    def simpleTaskStateChanges = new TestSimpleTaskStateChanges()
    def change1 = Mock(TaskStateChange)
    def change2 = Mock(TaskStateChange)

    def "fires all changes"() {
        when:
        final iterator = simpleTaskStateChanges.iterator()

        then:
        iterator.hasNext()
        iterator.next() == change1
        iterator.hasNext()
        iterator.next() == change2
    }

    def "caches all changes"() {
        when:
        simpleTaskStateChanges.iterator().next()
        simpleTaskStateChanges.iterator().next()

        then:
        simpleTaskStateChanges.addAllCount == 1
    }

    private class TestSimpleTaskStateChanges extends SimpleTaskStateChanges {
        int addAllCount;
        @Override
        protected void addAllChanges(List<TaskStateChange> changes) {
            changes.addAll([change1, change2])
            addAllCount++
        }
    }
}
