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

package org.gradle.model.internal.core

import spock.lang.Specification

class ModelPathTest extends Specification {
    def "path with single component is top level"() {
        def path = ModelPath.path("p")

        expect:
        path.depth == 1
        path.topLevel
        path.parent == null
        path.rootParent == null
    }

    def "can create path with separator in one component"() {
        def parent = ModelPath.path([name])
        def path = ModelPath.path([name, name])
        def child = ModelPath.path([name, name, name])

        expect:
        parent.topLevel
        parent.depth == 1
        parent.parent == null
        parent.rootParent == null
        path.parent == parent
        path.rootParent == parent
        path.name == name
        path.depth == 2
        path.toString() == "${name}.${name}"
        child.parent == path
        child.rootParent == parent
        child.depth == 3

        where:
        name       | _
        "."        | _
        "..."      | _
        "file.txt" | _
    }

    def "can create child with separator in name"() {
        def path = ModelPath.path("parent")
        def child = path.child(name)

        expect:
        child.parent == path
        child.name == name
        child.depth == 2
        child.toString() == "parent.${name}"

        where:
        name       | _
        "."        | _
        "..."      | _
        "file.txt" | _
    }
}
