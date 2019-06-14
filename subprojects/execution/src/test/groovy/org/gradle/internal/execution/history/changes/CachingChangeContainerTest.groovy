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

package org.gradle.internal.execution.history.changes


import spock.lang.Specification

class CachingChangeContainerTest extends Specification {
    def delegate = Mock(ChangeContainer)
    def change1 = Mock(Change)
    def change2 = Mock(Change)
    def change3 = Mock(Change)

    def cachingChanges = new CachingChangeContainer(2, delegate)
    def collectingVisitor = new CollectingChangeVisitor()

    def "caches all reported changes under cache size"() {
        when:
        cachingChanges.accept(new CollectingChangeVisitor())

        then:
        interaction {
            receivesChanges(change1, change2)
        }
        0 * _

        when:
        cachingChanges.accept(collectingVisitor)
        def reported = collectingVisitor.getChanges()

        then:
        0 * _

        and:
        reported == [change1, change2]
    }

    def "does not cache once reported changes exceed cache size"() {
        when:
        cachingChanges.accept(new CollectingChangeVisitor())

        then:
        interaction {
            receivesChanges(change1, change2, change3)
        }
        0 * _

        when:
        cachingChanges.accept(new CollectingChangeVisitor())
        cachingChanges.accept(collectingVisitor)
        def reported = collectingVisitor.changes

        then:
        interaction {
            receivesChanges(change1, change2, change3)
            receivesChanges(change3, change2, change1)
        }
        0 * _

        and:
        reported == [change3, change2, change1]
    }

    def "does not cache if visitor aborts visiting"() {
        when:
        cachingChanges.accept(new LimitingChangeVisitor(2, new CollectingChangeVisitor()))

        then:
        interaction {
            receivesChanges(change1, change2)
        }
        0 * _

        when:
        cachingChanges.accept(collectingVisitor)
        def reported = collectingVisitor.changes

        then:
        interaction {
            receivesChanges(change1, change2)
        }
        0 * _

        and:
        reported == [change1, change2]
    }

    private void receivesChanges(Change... changes) {
        1 * delegate.accept(_) >> { args ->
            ChangeVisitor visitor = args[0]
            for (change in changes) {
                if (!visitor.visitChange(change)) {
                    return false
                }
            }
            return true
        }
    }
}
