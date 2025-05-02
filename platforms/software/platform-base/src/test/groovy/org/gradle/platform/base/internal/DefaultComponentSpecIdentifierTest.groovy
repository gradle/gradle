/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.internal

import org.gradle.util.Matchers
import org.gradle.util.Path
import spock.lang.Specification


class DefaultComponentSpecIdentifierTest extends Specification {
    def "construct from project path and name"() {
        expect:
        def id = new DefaultComponentSpecIdentifier(":project", "name")
        id.parent == null
        id.name == "name"
        id.projectPath == ":project"
        id.path == Path.path("name")
        id.toString() == "name"
        id.projectScopedName == "name"
    }

    def "construct child"() {
        expect:
        def id = new DefaultComponentSpecIdentifier(":project", "name")
        def child = id.child("child")
        def grandchild = child.child("grandchild")

        child.parent == id
        child.path == Path.path("name:child")
        child.projectScopedName == "nameChild"

        grandchild.parent == child
        grandchild.path == Path.path("name:child:grandchild")
        grandchild.projectScopedName == "nameChildGrandchild"
    }

    def "ids are equal when name and parent and project are equal"() {
        expect:
        def topLevel = new DefaultComponentSpecIdentifier(":project", "name")
        def sameTopLevel = new DefaultComponentSpecIdentifier(":project", "name")
        def child = topLevel.child("child")
        def sameChild = sameTopLevel.child("child")
        def differentProject = new DefaultComponentSpecIdentifier(":other", "name")
        def differentName = topLevel.child("other")
        def differentParent = differentName.child("child")

        Matchers.strictlyEquals(topLevel, sameTopLevel)
        Matchers.strictlyEquals(child, sameChild)
        topLevel != differentProject
        child != differentName
        child != differentParent
    }
}
