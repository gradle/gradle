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

/**
 * Reads the predefined sub-groups of a root group for tests, in the declaration order kept by {@link PredefinedChildren}.
 */
class PredefinedRoots {

    /**
     * All predefined sub-groups of {@code root}, reserved ones and {@code Undefined} included.
     */
    static List<ProblemGroup> allPredefinedChildrenOf(ProblemGroup root) {
        def field = root.class.getDeclaredField("children")
        field.accessible = true
        (field.get(root) as PredefinedChildren).all() as List<ProblemGroup>
    }

    /**
     * The predefined sub-groups plugins can use: those whose field on the root has a public getter on the root's public
     * type. A sub-group without a getter is reserved for Gradle.
     */
    static List<ProblemGroup> publicPredefinedChildrenOf(ProblemGroup root) {
        def publicGetters = root.class.superclass.methods*.name as Set
        def fieldNames = root.class.declaredFields
            .findAll { ProblemGroup.isAssignableFrom(it.type) }
            .collectEntries { field ->
                field.accessible = true
                [(field.get(root)): field.name]
            }
        allPredefinedChildrenOf(root).findAll { publicGetters.contains("get" + fieldNames[it].capitalize()) }
    }
}
