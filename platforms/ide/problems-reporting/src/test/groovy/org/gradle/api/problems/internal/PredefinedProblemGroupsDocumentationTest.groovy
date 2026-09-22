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

import java.util.regex.Pattern

/**
 * The predefined hierarchy and its descriptions are repeated by hand in two places: a table in the user guide, and the
 * Javadoc of the public group types in {@code problems-api}. This test is the signal that one of them and the code have
 * drifted apart: it fails naming the row and the column, or the file and the group, that differ.
 * <p>
 * If this test fails, the code is the source of truth and the documentation is what needs to change, unless the failure
 * comes from the layout of the table or of the Javadoc:
 * <ul>
 *     <li>A predefined group was added, removed, renamed or reordered in a {@code Predefined*ProblemGroup} class, or a
 *     description constant in {@link PredefinedProblemGroupDescriptions} changed: edit the matching row of the table in
 *     {@code platforms/documentation/docs/src/docs/userguide/reference/plugin-development/reporting_problems.adoc}
 *     (section "Predefined problem groups"). Roots appear in the order of {@code DefaultProblemGroups.getRoots()},
 *     sub-groups in the declaration order of the root's fields, and the description cell must start with the exact text
 *     of the constant; a sentence may follow it, as in the Gradle row. A sub-group without a public getter on the root's
 *     public type is reserved for Gradle, such as {@code Gradle > Deprecation}: the table lists it with the suffix
 *     "(reserved for Gradle)" so that consumers of reports know the name, and no getter Javadoc is expected for it.</li>
 *     <li>Someone edited the table by hand, for example a rewording: restore the wording of the constant, or change the
 *     constant and the Javadoc of the matching {@code ProblemGroups} getter together if the new wording is better.</li>
 *     <li>The table was reformatted so that a row no longer spans three lines starting with {@code |}, or the header row
 *     "Root group | Predefined subgroups | What it covers" changed: adjust {@link #parseTable} and the header lookup;
 *     the hierarchy itself is then still in sync.</li>
 *     <li>The Javadoc check reports a difference: the description of a root group is quoted in the Javadoc of its getter
 *     in {@code ProblemGroups} and in the class Javadoc of its public type (for example {@code CompilationProblemGroup}),
 *     the description of a predefined sub-group in the Javadoc of its getter on that type. Each of these comments starts
 *     with {@code The {@code <group name>} root group.}, {@code The predefined {@code <group name>} root problem group.} or
 *     {@code The {@code <group name>} sub-group.}, followed by {@code <p>} and the text of the constant, wrapped at will
 *     and ended by an empty comment line or another {@code <p>}. Copy the text of the constant into the comment; if the
 *     comment shape changed on purpose, adjust {@link #DOCUMENTED_GROUP}.</li>
 * </ul>
 * The guide file and the sources of the public group types are declared as inputs of the test task in this module's build
 * script, so the test reruns when only the documentation changes; do not remove that wiring.
 */
class PredefinedProblemGroupsDocumentationTest extends Specification {

    private static final Pattern TABLE_ROW = Pattern.compile(/\n\|([A-Z][A-Za-z ]+)\n\|([^\n]*)\n\|([^\n]*)\n/)
    /**
     * A Javadoc comment that documents a predefined group: the opening sentence with the group name, a paragraph break,
     * and the description up to the next empty comment line or paragraph break.
     */
    private static final Pattern DOCUMENTED_GROUP = Pattern.compile(
        /\* The (?:predefined )?\{@code ([^}]+)\} (root group|root problem group|sub-group)\.\s*\n\s*\* <p>\s*\n((?:\s*\* (?!<p>)\S[^\n]*\n)+)/
    )
    /** Marks a sub-group in the table that has no public getter, so plugins cannot report into it. */
    private static final String RESERVED_MARKER = " (reserved for Gradle)"
    private static final String HOW_TO_FIX = "The code is the source of truth: update the table in the user guide, see the Javadoc of this test"
    private static final String HOW_TO_FIX_JAVADOC = "The code is the source of truth: update the Javadoc, see the Javadoc of this test"

    def "the hierarchy table in the user guide matches the predefined groups"() {
        given:
        def guide = new File(System.getProperty("org.gradle.problems.reportingProblemsGuide"))
        def table = parseTable(guide.getText("UTF-8"))
        def groups = DefaultProblemGroups.INSTANCE

        expect: "the roots appear in the spec order"
        assert table*.root == groups.roots*.name: "user guide table lists the root groups ${table*.root}, the code has ${groups.roots*.name}. ${HOW_TO_FIX} (${guide})"

        and: "each row lists the root's predefined sub-groups, marking the ones reserved for Gradle, and quotes its description"
        groups.roots.every { root ->
            def row = table.find { it.root == root.name }
            def publicNames = predefinedChildren(root)*.name
            def expectedSubgroups = allChildren(root).findAll { it.name != ProblemNames.UNDEFINED_NAME }.collect { publicNames.contains(it.name) ? it.name : it.name + RESERVED_MARKER }
            def expectedDescription = ((ProblemGroupInternal) root).description
            assert row.subgroups == (expectedSubgroups ?: ["_none_"]): "user guide row '${root.name}' lists sub-groups ${row.subgroups}, the code has ${expectedSubgroups}. ${HOW_TO_FIX} (${guide})"
            assert row.description.startsWith(expectedDescription): "user guide row '${root.name}' describes the group as '${row.description}', the code says '${expectedDescription}'. ${HOW_TO_FIX} (${guide})"
            true
        }
    }

    def "the Javadoc of the public group types quotes the descriptions of the predefined groups"() {
        given:
        def sources = new File(System.getProperty("org.gradle.problems.publicGroupTypes"))
        def groups = DefaultProblemGroups.INSTANCE

        expect: "ProblemGroups documents every root group on its getter, in the spec order"
        assertDocumented(new File(sources, "ProblemGroups.java"), "root group", groups.roots)

        and: "the public type of each root documents the root itself and each predefined sub-group on its getter"
        groups.roots.every { root ->
            def publicType = new File(sources, "${root.class.superclass.simpleName}.java")
            assertDocumented(publicType, "root problem group", [root])
            assertDocumented(publicType, "sub-group", predefinedChildren(root).findAll { it.name != ProblemNames.UNDEFINED_NAME })
            true
        }
    }

    private static void assertDocumented(File source, String kind, List<ProblemGroup> expected) {
        def documented = parseJavadoc(source.getText("UTF-8")).findAll { it.kind == kind }
        assert documented*.name == expected*.name: "${source.name} documents the ${kind}s ${documented*.name}, the code has ${expected*.name}. ${HOW_TO_FIX_JAVADOC} (${source})"
        expected.each { group ->
            def inJavadoc = documented.find { it.name == group.name }.description
            def inCode = ((ProblemGroupInternal) group).description
            assert inJavadoc == inCode: "${source.name} describes the ${kind} '${group.name}' as '${inJavadoc}', the code says '${inCode}'. ${HOW_TO_FIX_JAVADOC} (${source})"
        }
    }

    private static List<Map<String, String>> parseJavadoc(String java) {
        def documented = []
        def matcher = DOCUMENTED_GROUP.matcher(java)
        while (matcher.find()) {
            def description = matcher.group(3).readLines().collect { it.trim().replaceFirst(/^\* ?/, "") }.join(" ").trim()
            documented << [name: matcher.group(1), kind: matcher.group(2), description: description]
        }
        return documented
    }

    private static List<Map<String, Object>> parseTable(String adoc) {
        def start = adoc.indexOf("|Root group |Predefined subgroups |What it covers")
        assert start >= 0: "the hierarchy table was not found in the user guide"
        def end = adoc.indexOf("|===", start)
        def rows = []
        def matcher = TABLE_ROW.matcher(adoc.substring(start, end))
        while (matcher.find()) {
            rows << [root: matcher.group(1), subgroups: matcher.group(2).split(",")*.trim(), description: matcher.group(3)]
        }
        assert !rows.empty: "the hierarchy table in the user guide has no rows"
        return rows
    }

    /**
     * The predefined sub-groups of a root that plugins can use: the fields of the implementation, in declaration order, that
     * have a public getter on the root's public type. A field without such a getter is reserved for Gradle.
     */
    private static List<ProblemGroup> predefinedChildren(ProblemGroup root) {
        def publicGetters = root.class.superclass.methods*.name as Set
        children(root) { field -> publicGetters.contains("get" + field.name.capitalize()) }
    }

    /**
     * All predefined sub-groups of a root, reserved ones included: the fields of the implementation, in declaration order.
     */
    private static List<ProblemGroup> allChildren(ProblemGroup root) {
        children(root) { true }
    }

    private static List<ProblemGroup> children(ProblemGroup root, Closure<Boolean> fieldFilter) {
        root.class.declaredFields
            .findAll { ProblemGroup.isAssignableFrom(it.type) && fieldFilter(it) }
            .collect { field ->
                field.accessible = true
                field.get(root) as ProblemGroup
            }
    }
}
