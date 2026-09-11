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

import org.gradle.api.problems.LeafProblemGroup
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemGroups
import org.gradle.api.problems.RootProblemGroup
import org.gradle.api.problems.SubProblemGroup
import spock.lang.Specification

import java.lang.reflect.Modifier

class PredefinedProblemGroupsTest extends Specification {

    ProblemGroups groups = DefaultProblemGroups.INSTANCE

    // These are banned to avoid repetitive, unhelpful, or vague descriptions. "You" and "your" are banned to avoid blaming the user for problems that may be caused by Gradle or plugins.
    static final List<String> BANNED_WORDS = ["problem", "error", "warning", "issue", "thing", "stuff", "you", "your"]

    def "all root groups are reachable, in spec order, with name, description and no parent"() {
        expect:
        DefaultProblemGroups.INSTANCE.roots*.name == [
            "Gradle", "Dependencies", "Transformation", "Compilation", "External Processes",
            "Packaging", "Verification", "Documentation", "Provisioning", "Delivery", "Others"
        ]
        DefaultProblemGroups.INSTANCE.roots.every { it.parent == null && it.description && it.displayName == it.name }
        DefaultProblemGroups.INSTANCE.roots.every { it instanceof ProblemGroupInternal }
        DefaultProblemGroups.INSTANCE.roots.every { it instanceof RootProblemGroup || it.is(groups.gradle) }
        DefaultProblemGroups.INSTANCE.findRoot("Compilation").is(groups.compilation)
        DefaultProblemGroups.INSTANCE.findRoot("compilation") == null
    }

    def "every predefined group has a description, displayName equal to name, and the right parent"() {
        expect:
        for (root in DefaultProblemGroups.INSTANCE.roots) {
            for (child in predefinedChildren(root)) {
                assert child.parent.is(root)
                assert child.description
                assert child.displayName == child.name
                assert child instanceof ProblemGroupInternal
                assert child.name == ProblemGroupSupport.validateGroupName(child.name)
            }
        }
    }

    def "spec groups are present with the expected names"() {
        expect:
        predefinedChildren(groups.gradle)*.name == ["Build Cache", "Build Definition", "Build Logic", "Configuration Cache", "Deprecation", "DSL Evaluation", "Invocation", "Isolated Projects", "Plugin Validation", "Undefined"]
        predefinedChildren(groups.dependencies)*.name == ["Declaration", "Locking", "Graph Resolution", "Artifact Resolution", "Verification", "Undefined"]
        predefinedChildren(groups.transformation)*.name == ["Binary Transformation", "Code Transformation", "Resource Transformation", "Undefined"]
        predefinedChildren(groups.compilation)*.name == ["C++", "Groovy", "Java", "Kotlin", "Scala", "Swift", "Undefined"]
        predefinedChildren(groups.externalProcesses)*.name == ["Application", "Service", "Tool", "Undefined"]
        predefinedChildren(groups.packaging)*.name == ["Distributions", "JAR", "Native Linking", "Signing", "Undefined"]
        predefinedChildren(groups.verification)*.name == ["Code Coverage", "Code Quality", "Security", "Testing", "Undefined"]
        predefinedChildren(groups.documentation)*.name == ["Javadoc", "Groovydoc", "Scaladoc", "Undefined"]
        predefinedChildren(groups.provisioning)*.name == ["Infrastructure", "Tools and Toolchains", "Undefined"]
        predefinedChildren(groups.delivery)*.name == ["Publishing", "Deployment", "Undefined"]
        predefinedChildren(groups.others)*.name == ["Undefined"]
    }

    def "gradle sub-groups are terminal, open root sub-groups are extensible"() {
        expect:
        predefinedChildren(groups.gradle).every { it instanceof LeafProblemGroup }
        predefinedChildren(groups.compilation).findAll { it.name != "Undefined" }.every { it instanceof SubProblemGroup }
        !(groups.gradle instanceof RootProblemGroup)
        !groups.gradle.metaClass.respondsTo(groups.gradle, "group", String)
    }

    def "every root and every open sub-group has an Undefined group with a templated description"() {
        expect:
        for (root in DefaultProblemGroups.INSTANCE.roots) {
            def undefined = root.undefined
            assert undefined.name == "Undefined"
            assert undefined.parent.is(root)
            assert undefined.description == "Problems without an explicitly defined ${root.name} sub-group"
            assert undefined instanceof LeafProblemGroup
        }
        groups.compilation.java.undefined.description == "Problems without an explicitly defined Java sub-group"
        groups.compilation.java.undefined.is(groups.compilation.java.undefined)
        groups.compilation.java.undefined.parent.is(groups.compilation.java)
        groups.transformation.group("KMP").undefined.description == "Problems without an explicitly defined KMP sub-group"
        // user groups are not memoized, so neither is their Undefined group; equality is structural
        groups.transformation.group("KMP").undefined == groups.transformation.group("KMP").undefined
    }

    def "descriptions do not use banned words (except Others and Undefined, which may say 'problems')"() {
        def constants = PredefinedProblemGroupDescriptions.declaredFields.findAll {
            Modifier.isStatic(it.modifiers) && it.type == String && it.name != "UNDEFINED_TEMPLATE" && it.name != "OTHERS"
        }

        expect:
        constants.size() > 40
        for (field in constants) {
            String description = field.get(null)
            for (word in BANNED_WORDS) {
                assert !(description.toLowerCase() =~ /\b${word}s?\b/): "Description ${field.name} contains banned word '${word}': ${description}"
            }
            assert description.endsWith(".")
        }
        PredefinedProblemGroupDescriptions.OTHERS == "Problems that do not fit in any other group."
    }

    def "group(name) returns the predefined sibling on exact match"() {
        expect:
        groups.compilation.group("Java").is(groups.compilation.java)
        groups.compilation.group("Java").description == PredefinedProblemGroupDescriptions.COMPILATION_JAVA
        groups.gradle.buildCache.description == PredefinedProblemGroupDescriptions.GRADLE_BUILD_CACHE
    }

    def "group('#name') creates a user group distinct from the predefined sibling '#sibling', since names are case-sensitive"() {
        // Not rejecting such names keeps plugins working when Gradle later adds a predefined group that only differs by case
        when:
        def group = root.group(name)

        then:
        group.name == name
        group.description == null
        group != root."${sibling.uncapitalize()}"
        !group.is(root."${sibling.uncapitalize()}")

        where:
        root                                      | name             | sibling
        DefaultProblemGroups.INSTANCE.compilation | "java"           | "Java"
        DefaultProblemGroups.INSTANCE.compilation | "JAVA"           | "Java"
        DefaultProblemGroups.INSTANCE.packaging   | "S\u0131gning"   | "Signing"   // dotless i
        DefaultProblemGroups.INSTANCE.compilation | "\u212Aotlin"    | "Kotlin"    // Kelvin sign
    }

    def "group(name) rejects the reserved name '#name' at every level"() {
        when:
        groups.compilation.group(name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("reserved problem group name")

        when:
        groups.compilation.java.group(name)

        then:
        thrown(IllegalArgumentException)

        when:
        groups.transformation.group("KMP").group(name)

        then:
        thrown(IllegalArgumentException)

        where:
        name << ["Undefined", "undefined", "UNDEFINED"]
    }

    def "group(name) creates user groups without description, not memoized but equal"() {
        when:
        def kmp = groups.transformation.group("KMP")
        def kmpAgain = groups.transformation.group("KMP")
        def js = kmp.group("JavaScript")

        then:
        kmp instanceof SubProblemGroup
        kmp.name == "KMP"
        kmp.displayName == "KMP"
        kmp.description == null
        kmp.parent.is(groups.transformation)
        kmp == kmpAgain
        kmp.hashCode() == kmpAgain.hashCode()
        !kmp.is(kmpAgain)
        js instanceof LeafProblemGroup
        js.parent == kmp
        js.description == null
        js.toString() == "Transformation > KMP > JavaScript"
        // user siblings are not guarded against case differences
        groups.transformation.group("kmp") != kmp
    }

    def "group(name) on a predefined sub-group creates a terminal user group below it"() {
        when:
        def lombok = groups.compilation.java.group("Lombok")
        def id = lombok.problem("Annotation processing failed")

        then:
        lombok instanceof LeafProblemGroup
        lombok.name == "Lombok"
        lombok.description == null
        lombok.parent.is(groups.compilation.java)
        lombok == groups.compilation.java.group("Lombok")
        id.group.is(lombok)
        roundTrip(lombok) == lombok
        roundTrip(lombok).parent.is(groups.compilation.java)
    }

    def "group(name) validates names"() {
        // the rules themselves are covered by ProblemGroupSupportTest; this only checks that both entry points validate
        when:
        groups.compilation.group("a\tb")

        then:
        thrown(IllegalArgumentException)

        when:
        groups.compilation.java.group("a\tb")

        then:
        thrown(IllegalArgumentException)
    }

    def "problem(name) creates an id in the group with displayName equal to name"() {
        when:
        def id = groups.compilation.java.problem("Unused import")
        def leafId = groups.transformation.group("KMP").group("JavaScript").problem("Something failed")
        def undefinedId = groups.others.undefined.problem("Whatever")
        def gradleId = groups.gradle.deprecation.problem("Deprecated feature used")

        then:
        id instanceof DefaultProblemId
        id.name == "Unused import"
        id.displayName == "Unused import"
        id.group.is(groups.compilation.java)
        gradleId.group.is(groups.gradle.deprecation)
        leafId.group == groups.transformation.group("KMP").group("JavaScript")
        undefinedId.group.is(groups.others.undefined)
    }

    def "problem(name) validates names"() {
        // the rules themselves are covered by ProblemGroupSupportTest; this only checks that both entry points validate
        when:
        groups.compilation.java.problem("a\tb")

        then:
        thrown(IllegalArgumentException)

        when:
        groups.compilation.java.undefined.problem("a\tb")

        then:
        thrown(IllegalArgumentException)
    }

    def "predefined groups are equal to structurally equal legacy groups, symmetrically"() {
        def legacyRoot = ProblemGroup.create("Compilation", "whatever")
        def legacyJava = ProblemGroup.create("Java", "whatever", legacyRoot)
        def legacyUser = ProblemGroup.create("KMP", "KMP", groups.transformation)

        expect:
        groups.compilation == legacyRoot
        legacyRoot == groups.compilation
        groups.compilation.hashCode() == legacyRoot.hashCode()
        groups.compilation.java == legacyJava
        legacyJava == groups.compilation.java
        groups.compilation.java.hashCode() == legacyJava.hashCode()
        groups.transformation.group("KMP") == legacyUser
        legacyUser == groups.transformation.group("KMP")
        groups.compilation.java != groups.compilation.kotlin
        groups.compilation != groups.gradle
        // legacy kebab-case internal groups are distinct
        groups.compilation != ProblemGroup.create("compilation", "Compilation")
    }

    def "serialization round trip resolves predefined groups to the canonical instances and keeps descriptions"() {
        expect:
        roundTrip(groups.compilation).is(groups.compilation)
        roundTrip(groups.compilation.java).is(groups.compilation.java)
        roundTrip(groups.compilation.java.undefined).is(groups.compilation.java.undefined)
        roundTrip(groups.gradle.buildCache).is(groups.gradle.buildCache)
        roundTrip(groups.gradle.undefined).is(groups.gradle.undefined)
        roundTrip(groups.others.undefined).is(groups.others.undefined)
        roundTrip(groups.compilation.java).description == PredefinedProblemGroupDescriptions.COMPILATION_JAVA
    }

    def "serialization round trip re-creates user groups below the canonical predefined parent"() {
        def kmp = groups.transformation.group("KMP")
        def js = kmp.group("JavaScript")

        when:
        def kmpCopy = roundTrip(kmp)
        def jsCopy = roundTrip(js)
        def undefinedCopy = roundTrip(kmp.undefined)

        then:
        kmpCopy == kmp
        kmpCopy.parent.is(groups.transformation)
        kmpCopy.description == null
        jsCopy == js
        jsCopy.parent == kmp
        jsCopy instanceof LeafProblemGroup
        undefinedCopy == kmp.undefined
        undefinedCopy.description == "Problems without an explicitly defined KMP sub-group"
    }

    def "reading a serialized path that names an unknown root or an unknown child of the closed Gradle root fails"() {
        // legacy groups serialize as plain beans; only the predefined hierarchy goes through the name path
        when:
        roundTrip(SerializedProblemGroup.of(ProblemGroup.create("Nope", "Nope")))

        then:
        def unknownRoot = thrown(InvalidObjectException)
        unknownRoot.message == "Unknown predefined root problem group 'Nope'"

        when:
        roundTrip(SerializedProblemGroup.of(ProblemGroup.create("Nope", "Nope", groups.gradle)))

        then:
        def unknownChild = thrown(InvalidObjectException)
        unknownChild.message == "Unknown predefined problem group 'Nope'"
    }

    def "serialized form only carries the path, not the siblings"() {
        when:
        def bytes = serialize(groups.compilation.java)
        def text = new String(bytes, "ISO-8859-1")

        then:
        text.contains("Compilation")
        text.contains("Java")
        !text.contains("Kotlin")
        !text.contains("Swift")
        !text.contains("compiler invocation")
        bytes.length < 400
    }

    def "legacy groups with a predefined parent survive serialization"() {
        def legacy = ProblemGroup.create("Legacy", "Legacy", groups.compilation)

        when:
        def copy = roundTrip(legacy)

        then:
        copy == legacy
        copy.parent.is(groups.compilation)
    }

    def "problem ids created from predefined groups survive serialization"() {
        def id = groups.compilation.java.problem("Unused import")

        when:
        def copy = roundTrip(id)

        then:
        copy == id
        copy.group.is(groups.compilation.java)
    }

    /**
     * The predefined children of a root, in declaration order of the implementation's fields (which follows the spec).
     */
    private static List<ProblemGroup> predefinedChildren(ProblemGroup root) {
        root.class.declaredFields
            .findAll { ProblemGroup.isAssignableFrom(it.type) }
            .collect { field ->
                field.accessible = true
                field.get(root) as ProblemGroup
            }
    }

    private static byte[] serialize(Object o) {
        def bos = new ByteArrayOutputStream()
        new ObjectOutputStream(bos).withCloseable { it.writeObject(o) }
        bos.toByteArray()
    }

    private static <T> T roundTrip(T o) {
        new ObjectInputStream(new ByteArrayInputStream(serialize(o))).readObject() as T
    }
}
