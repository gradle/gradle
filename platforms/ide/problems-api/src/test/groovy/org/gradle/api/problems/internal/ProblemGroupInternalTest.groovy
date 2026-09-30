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

package org.gradle.api.problems.internal

import org.gradle.api.problems.ProblemGroup
import spock.lang.Specification

class ProblemGroupInternalTest extends Specification {

    def "equality is structural on name and parent, across implementations"() {
        def root = ProblemGroup.create("Root", "Root display")
        def sameRoot = ProblemGroup.create("Root", "different display")
        def otherRoot = ProblemGroup.create("Other", "Root display")
        def child = ProblemGroup.create("Child", "Child", root)
        def sameChild = ProblemGroup.create("Child", "Child", sameRoot)
        def foreign = new ForeignGroup("Child", root)

        expect:
        root == sameRoot
        root.hashCode() == sameRoot.hashCode()
        root != otherRoot
        child == sameChild
        child.hashCode() == sameChild.hashCode()
        child != root
        // the same structural rules apply to other implementations that delegate to ProblemGroupInternal
        child == foreign
        ProblemGroupInternal.structurallyEquals(foreign, child)
        ProblemGroupInternal.structuralHashCode(foreign) == child.hashCode()
        !ProblemGroupInternal.structurallyEquals(root, "Root")
        !ProblemGroupInternal.structurallyEquals(root, null)
        !root.equals("Root")
    }

    def "groups created through the legacy API have no description"() {
        expect:
        ((ProblemGroupInternal) ProblemGroup.create("Root", "Root")).description == null
    }

    private static class ForeignGroup extends ProblemGroup {
        private final String name
        private final ProblemGroup parent

        ForeignGroup(String name, ProblemGroup parent) {
            this.name = name
            this.parent = parent
        }

        String getName() { name }

        String getDisplayName() { name }

        ProblemGroup getParent() { parent }
    }
}
