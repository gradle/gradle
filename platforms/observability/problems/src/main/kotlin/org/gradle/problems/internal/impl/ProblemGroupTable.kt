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
package org.gradle.problems.internal.impl

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.UndefinedProblemGroup
import org.gradle.api.problems.internal.ProblemGroupInternal
import org.gradle.problems.internal.report.model.JsProblemGroup
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects the groups of the reported problems and lists them in the order the problems report shows them.
 * Problems are reported from many threads, so [add] may be called concurrently.
 *
 * @param rootGroups the predefined root groups, in the order the report shows them, ending with Others
 */
internal class ProblemGroupTable(private val rootGroups: List<ProblemGroup>) {

    private val groupsByPath = ConcurrentHashMap<List<String>, ProblemGroupInternal>()

    /**
     * Adds the group of the given problem id and the ancestors of that group.
     * The problem reporter has already replaced legacy groups that match a predefined group with the predefined one.
     */
    fun add(problemId: ProblemId) {
        val chain = problemId.group.chainFromRoot()
        chain.indices.forEach { index ->
            groupsByPath.putIfAbsent(chain.subList(0, index + 1).map { it.name }, chain[index])
        }
    }

    /**
     * Lists the groups added so far, depth first: each group is followed by its sub-groups. Siblings are ordered by name,
     * ignoring case, except that predefined root groups come first, in the order of [rootGroups], the last predefined
     * root group (Others) comes after all other root groups, and `Undefined` groups come last.
     */
    fun toJsProblemGroups(): List<JsProblemGroup> {
        val groups = HashMap(groupsByPath)
        val childrenByParentPath = groups.keys.groupBy { it.dropLast(1) }
        val siblingOrder = compareBy<List<String>> { path -> if (path.size == 1) rootRank(groups.getValue(path)) else 0 }
            .thenBy { path -> groups.getValue(path) is UndefinedProblemGroup }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { path -> path.last() }
            .thenBy { path -> path.last() }

        fun groupsBelow(parentPath: List<String>): List<JsProblemGroup> =
            childrenByParentPath[parentPath].orEmpty().sortedWith(siblingOrder).flatMap { path ->
                listOf(JsProblemGroup(path, groups.getValue(path).description)) + groupsBelow(path)
            }

        return groupsBelow(emptyList())
    }

    // Other roots come before the last predefined root, Others, so that Others is always last.
    private fun rootRank(group: ProblemGroupInternal): Int =
        when (val index = rootGroups.indexOfFirst { it == group }) {
            -1 -> rootGroups.lastIndex
            rootGroups.lastIndex -> rootGroups.size
            else -> index
        }
}
