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

package org.gradle.api.internal.initialization

import spock.lang.Specification

class ClassLoaderScopeIdentifierTest extends Specification {
    private ClassLoaderScopeIdentifier root = new ClassLoaderScopeIdentifier(null, "root")

    def "equality"() {
        expect:
        root.localId() == root.localId()
        root.localId() != root.exportId()
        root.exportId() != root.localId()
        root.exportId() == root.exportId()
    }

    def "creates child"() {
        def child = root.child(root.name)

        expect:
        child.localId() == child.localId()
        child.localId() != child.exportId()
        child.exportId() != child.localId()
        child.exportId() == child.exportId()
    }

    def "children with same name are equivalent"() {
        def child1 = root.child(root.name)
        def child2 = root.child(root.name)

        expect:
        child1.localId() == child2.localId()
        child1.exportId() == child2.exportId()
    }

    def "children with different name are not equal"() {
        def child1 = root.child(root.name)
        def child2 = root.child(root.name + "changed")

        expect:
        child1.localId() != child2.localId()
        child1.exportId() != child2.exportId()
    }

}
