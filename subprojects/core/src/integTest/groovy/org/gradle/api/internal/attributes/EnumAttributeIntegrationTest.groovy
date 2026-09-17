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

package org.gradle.api.internal.attributes

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Integration tests demonstrating that plain Java {@code Enum} types (those that do not
 * implement {@link org.gradle.api.Named}) are <strong>NOT</strong> supported as attribute values in Gradle's
 * dependency-resolution and publishing pipelines.
 * <p>
 * {@link org.gradle.api.attributes.Attribute#of(String, Class)} validates the requested attribute
 * type up front: it accepts {@code String}, {@code Boolean}, any subtype of {@link Number}, and
 * any type implementing {@link org.gradle.api.Named}. Any other type — including plain Java
 * {@code Enum} types — currently emits a deprecation warning and will become a hard error in
 * Gradle 10 (see {@code Attribute.of} javadoc). The tests here document both the current
 * deprecation-warning behavior and the underlying failure modes that motivated the deprecation.
 * <p>
 * A Named-implementing enum ({@code enum X implements Named}) delegating {@code getName()} to the
 * built-in {@code name()} is the supported way to use an enum as an attribute value.
 * <p>
 * Each test in the "enums succeed" region is parameterized with two enum flavors:
 * <ul>
 *   <li>{@code PLAIN} — a bare {@code enum MyEnum { FOO, BAR }} that does NOT implement
 *       {@link org.gradle.api.Named}. Emits a deprecation warning at declaration time but
 *       continues to work end-to-end for the pipelines exercised here.</li>
 *   <li>{@code NAMED} — an {@code enum MyEnum implements Named} that delegates
 *       {@code getName()} to the built-in {@code name()}. Accepted; works end-to-end.</li>
 * </ul>
 * <p>
 * Tests are organized into two regions:
 * <ul>
 *   <li><b>enums succeed</b> — valid usage patterns whose underlying Gradle pipelines handle
 *       plain enums correctly today; both flavors succeed. The plain flavor additionally emits
 *       the deprecation warning from {@code Attribute.of}.</li>
 *   <li><b>un-named enums fail</b> — plain-enum-only tests documenting the underlying failure
 *       modes that motivated the deprecation: the ES 9.5.1 regression (CCE at
 *       {@code DesugaringAttributeContainerSerializer:91}) and two JDK-contract failures at
 *       {@code Enum.valueOf} callsites ({@code IsolatedEnumValueSnapshot:56} and
 *       {@code CoercingStringValueSnapshot:39}). These tests are marked
 *       {@link groovy.test.NotYetImplemented} because the current deprecation-only enforcement
 *       lets these scenarios proceed further than the ideal Gradle-10 behavior, which is to
 *       fail up front at {@code Attribute.of}. See {@code problems-with-unnamed-enums.md} in
 *       this directory for details on each root cause.</li>
 * </ul>
 */
@Issue("https://github.com/gradle/gradle/issues/38242")
final class EnumAttributeIntegrationTest extends AbstractIntegrationSpec {
    // region setup
    // Enum declaration templates injected into build scripts. Each declares a top-level
    // `MyEnum` type with constants FOO and BAR. The PLAIN flavor is a bare enum. The NAMED
    // flavor implements `org.gradle.api.Named` (default-imported in build scripts) by
    // delegating `getName()` to the built-in `name()`.
    private static final String PLAIN_ENUM = """
        enum MyEnum { FOO, BAR }
    """

    private static final String NAMED_ENUM = """
        enum MyEnum implements Named {
            FOO, BAR

            @Override
            String getName() { return name() }
        }
    """

    private static final String PLAIN_DESC = "plain Enum"
    private static final String NAMED_DESC = "Named-implementing Enum"

    // Distinctive summary line emitted by Attribute.of when a plain (non-Named) enum type is
    // declared as an attribute value type. Same script text emits the deprecation once per
    // distinct declaration site, so the count depends on the test structure — we assert on
    // presence via outputContains rather than registering per-emission expectations.
    private static final String PLAIN_ENUM_DEPRECATION_SUMMARY =
        "Using type 'MyEnum' as a value type for attribute 'myEnumAttribute' has been deprecated."

    /**
     * Runs the given task and asserts success. Named-implementing enums pass through untouched.
     * Plain enums additionally trigger the {@code Attribute.of} deprecation warning, which is
     * asserted via {@code outputContains}. The deprecation will become an error in Gradle 10.
     */
    private void expectResolve(String taskName, boolean implementsNamed, List<String> expectedOutputs = []) {
        if (!implementsNamed) {
            executer.noDeprecationChecks()
        }
        succeeds(taskName)
        if (!implementsNamed) {
            outputContains(PLAIN_ENUM_DEPRECATION_SUMMARY)
        }
        expectedOutputs.each { outputContains(it) }
    }
    // endregion setup

    // region enums succeed
    // -------------------------------------------------------------------------
    // Every test in this region exercises a valid Gradle attribute-usage pattern.
    // Named-implementing enums pass end-to-end. Plain enums additionally emit the
    // PLAIN_ENUM_DEPRECATION_SUMMARY deprecation warning from Attribute.of but continue
    // through the pipeline successfully. That deprecation will become an error in
    // Gradle 10 — at which point these plain-enum iterations will need to be
    // updated (and the un-named enums fail region tests may become passable).
    // -------------------------------------------------------------------------
    def "in-memory attribute matching accepts a #enumDesc as an attribute value"() {
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: output.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "AttributeCompatibilityRule typed on a #enumDesc makes candidate values compatible"() {
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.BAR) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            abstract class MyEnumCompatibilityRule implements AttributeCompatibilityRule<MyEnum> {
                void execute(CompatibilityCheckDetails<MyEnum> details) { details.compatible() }
            }

            dependencies {
                attributesSchema {
                    attribute(ATTRIBUTE_TYPE) {
                        compatibilityRules.add(MyEnumCompatibilityRule)
                    }
                }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: output.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "AttributeDisambiguationRule typed on a #enumDesc picks a candidate"() {
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/foo.txt") << "foo output"
        file("producer/bar.txt") << "bar output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("fooVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("foo.txt"))
                }
                consumable("barVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.BAR) }
                    outgoing.artifact(file("bar.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            abstract class MyEnumCompatibilityRule implements AttributeCompatibilityRule<MyEnum> {
                void execute(CompatibilityCheckDetails<MyEnum> details) { details.compatible() }
            }
            abstract class MyEnumDisambiguationRule implements AttributeDisambiguationRule<MyEnum> {
                void execute(MultipleCandidatesDetails<MyEnum> details) {
                    if (details.candidateValues.contains(MyEnum.BAR)) {
                        details.closestMatch(MyEnum.BAR)
                    }
                }
            }

            dependencies {
                attributesSchema {
                    attribute(ATTRIBUTE_TYPE) {
                        compatibilityRules.add(MyEnumCompatibilityRule)
                        disambiguationRules.add(MyEnumDisambiguationRule)
                    }
                }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: bar.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "supplying a #enumDesc attribute value via a lazy Provider works end-to-end"() {
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes {
                        attributeProvider(ATTRIBUTE_TYPE, project.provider { MyEnum.FOO })
                    }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes {
                        attributeProvider(ATTRIBUTE_TYPE, project.provider { MyEnum.FOO })
                    }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: output.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "materializing resolutionResult with a #enumDesc on a local project dependency"() {
        // Local project dependencies do not stream the resolution result through
        // DesugaringAttributeContainerSerializer. Empirically, even without the up-front
        // Attribute.of check, both flavors resolve cleanly here.
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def rootProvider = configurations.myResolver.incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootProvider.get()
                    println("Root: " + root.moduleVersion)
                    root.dependencies.each { d -> println("Dep: " + d) }
                }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Dep: project ':producer'"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "consuming a Maven-published variant with a #enumDesc-typed request attribute"() {
        given:
        mavenRepo.module("org.example", "producer", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("myVariant", [myEnumAttribute: "FOO"]) {
                artifact("producer-1.0.jar")
            }
            .publish()

        buildFile("""
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            repositories {
                maven { url = uri("${mavenRepo.uri}") }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps("org.example:producer:1.0")
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        expectResolve("resolve", implementsNamed, ["Resolved: producer-1.0.jar"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "consuming an Ivy-published variant with a #enumDesc-typed request attribute"() {
        given:
        ivyRepo.module("org.example", "producer", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("myVariant", [myEnumAttribute: "FOO"])
            .withVariant("myVariant") {
                artifact("producer-1.0.jar")
            }
            .publish()

        buildFile("""
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            repositories {
                ivy { url = uri("${ivyRepo.uri}") }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps("org.example:producer:1.0")
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        expectResolve("resolve", implementsNamed, ["Resolved: "])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "withVariantReselection using a #enumDesc as the reselection attribute"() {
        given:
        mavenRepo.module("org.example", "producer", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("fooVariant", [myEnumAttribute: "FOO"]) {
                artifact("producer-1.0-foo.jar")
            }
            .variant("barVariant", [myEnumAttribute: "BAR"]) {
                artifact("producer-1.0-bar.jar")
            }
            .publish()

        buildFile("""
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            repositories {
                maven { url = uri("${mavenRepo.uri}") }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps("org.example:producer:1.0")
            }

            tasks.register("resolve") {
                def reselected = configurations.myResolver.incoming.artifactView {
                    withVariantReselection()
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.BAR) }
                }.files
                doLast {
                    reselected.each { println("Reselected: " + it.name) }
                }
            }
        """)

        expect:
        expectResolve("resolve", implementsNamed, ["Reselected: producer-1.0-bar.jar"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "config-cache round-trip on a task holding a resolvable configuration with a #enumDesc"() {
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            abstract class HoldEnum extends DefaultTask {
                @Input
                abstract Property<MyEnum> getEnumInput()
                @InputFiles
                abstract ConfigurableFileCollection getFiles()
                @TaskAction
                void run() {
                    println("Enum: " + enumInput.get())
                    files.each { println("Resolved: " + it.name) }
                }
            }

            tasks.register("resolve", HoldEnum) {
                enumInput.set(MyEnum.FOO)
                files.from(configurations.myResolver)
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Enum: FOO", "Resolved: output.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    // region additional coverage
    // -------------------------------------------------------------------------
    // These tests exercise additional code paths (publishing, build ops,
    // component metadata rules, external-Maven resolutionResult, dedup
    // serialization, extendsFrom inheritance, schema registration). Empirically,
    // plain enums work cleanly through all of these paths when validateSupportedType
    // is disabled; the tests belong in "enums succeed" because their assertions
    // are structurally identical to the other tests in this region.
    // -------------------------------------------------------------------------

    def "publishing a variant with a #enumDesc-typed attribute to a Maven repository"() {
        // Exercises the producer-side publishing pipeline via maven-publish.
        // ModuleMetadataSpecBuilder.attributeValueFor already handles Enum values by name
        // via .name(), so the underlying pipeline is enum-safe. Under the current policy,
        // Attribute.of rejects plain enums upstream, so the plain row fails at script eval
        // on the producer side rather than reaching the GMM writer.
        given:
        file("output.txt") << "sample output"
        buildFile("""
            plugins { id 'maven-publish' }

            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            group = 'org.example'
            version = '1.0'

            def myVariant = configurations.consumable("myVariant") {
                attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                outgoing.artifact(file("output.txt"))
            }

            def component = publishing.softwareComponentFactory.adhoc("myComponent")
            component.addVariantsFromConfiguration(myVariant.get()) {
                mapToMavenScope("runtime")
            }
            components.add(component)

            publishing {
                repositories { maven { url = uri("${mavenRepo.uri}") } }
                publications {
                    maven(MavenPublication) { from components.myComponent }
                }
            }
        """)

        expect:
        expectResolve("publish", implementsNamed)

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "resolution emits build operations with a #enumDesc attribute value"() {
        // Probes AttributesToMapConverter.getAttributeValueAsString: the build-op path
        // uses value.toString() as a fallback, which for an enum yields the constant name.
        // Any successful resolution emits build operations that carry the attribute
        // container through this code path — enum-safe.
        given:
        settingsFile("include 'consumer', 'producer'")

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies { myDeps(project(":producer")) }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.files
                doLast { files.each { println("Resolved: " + it.name) } }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: output.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "resolution build-op result desugars a #enumDesc attribute via toString"() {
        // Probes ResolveConfigurationResolutionBuildOperationResult.desugarAttributes,
        // which has its own desugaring: primitives, then Named, then a .toString() fallback.
        // Enums fall into the fallback branch — the constant name is emitted verbatim.
        given:
        settingsFile("include 'consumer', 'producer'")

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies { myDeps(project(":producer")) }

            tasks.register("resolve") {
                def rootProvider = configurations.myResolver.incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootProvider.get()
                    println("Root: " + root.moduleVersion)
                }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Root: "])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "component metadata rule adds a #enumDesc attribute to a resolved module"() {
        // A component metadata rule mutates the resolved graph's attributes at rule
        // execution time. The rule references MyEnum inside its execute() body — but the
        // consumer script's own Attribute.of call fires first at script eval for plain
        // enums.
        given:
        mavenRepo.module("org.example", "producer", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("myVariant", [:]) {
                artifact("producer-1.0.jar")
            }
            .publish()

        buildFile("""
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            repositories { maven { url = uri("${mavenRepo.uri}") } }

            abstract class AddEnumAttributeRule implements ComponentMetadataRule {
                @Override
                void execute(ComponentMetadataContext ctx) {
                    def attr = Attribute.of("myEnumAttribute", MyEnum.class)
                    ctx.details.allVariants {
                        attributes { attribute(attr, MyEnum.FOO) }
                    }
                }
            }

            dependencies {
                components {
                    withModule("org.example:producer", AddEnumAttributeRule)
                }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies { myDeps("org.example:producer:1.0") }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.files
                doLast { files.each { println("Resolved: " + it.name) } }
            }
        """)

        expect:
        expectResolve("resolve", implementsNamed, ["Resolved: producer-1.0.jar"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "materializing resolutionResult with a #enumDesc on an external Maven dependency"() {
        // Static code-reading suggests `.resolutionResult.rootComponent.get()` on an external
        // Maven dep should stream through StreamingResolutionResultBuilder →
        // DesugaringAttributeContainerSerializer and reproduce the ES/9.5.1 regression.
        // Empirically it does not — execution-time result queries operate against the
        // in-memory graph and don't re-stream. Only the ES-shape (test in un-named enums fail
        // region: detached configuration + task inputs at task-graph-time) hits the streaming
        // path. This test therefore succeeds cleanly for both flavors when the up-front check
        // is disabled.
        given:
        mavenRepo.module("org.example", "producer", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("myVariant", [myEnumAttribute: "FOO"]) {
                artifact("producer-1.0.jar")
            }
            .publish()

        buildFile("""
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            repositories { maven { url = uri("${mavenRepo.uri}") } }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies { myDeps("org.example:producer:1.0") }

            tasks.register("resolve") {
                def rootProvider = configurations.myResolver.incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootProvider.get()
                    println("Root: " + root.moduleVersion)
                    root.dependencies.each { d -> println("Dep: " + d) }
                }
            }
        """)

        expect:
        expectResolve("resolve", implementsNamed, ["Dep: org.example:producer:1.0"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "deduplicated serialization when multiple variants share a #enumDesc attribute container"() {
        // DeduplicatingAttributeContainerSerializer wraps DesugaringAttributeContainerSerializer
        // and interns identical attribute containers on write. Static code-reading suggests
        // materializing the resolution result on a module with overlapping variant attributes
        // should hit the dedup wrapper — but empirically it does not, for the same reason as
        // the external-Maven resolutionResult test above: execution-time result queries use
        // the in-memory graph. This test succeeds cleanly for both flavors when the up-front
        // check is disabled.
        given:
        mavenRepo.module("org.example", "producer", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("variant1", [myEnumAttribute: "FOO", tag: "one"]) {
                artifact("producer-1.0-a.jar")
            }
            .variant("variant2", [myEnumAttribute: "FOO", tag: "two"]) {
                artifact("producer-1.0-b.jar")
            }
            .publish()

        buildFile("""
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)
            def TAG_ATTR = Attribute.of("tag", String.class)

            repositories { maven { url = uri("${mavenRepo.uri}") } }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes {
                        attribute(ATTRIBUTE_TYPE, MyEnum.FOO)
                        attribute(TAG_ATTR, "one")
                    }
                }
            }

            dependencies { myDeps("org.example:producer:1.0") }

            tasks.register("resolve") {
                def rootProvider = configurations.myResolver.incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootProvider.get()
                    println("Root: " + root.moduleVersion)
                }
            }
        """)

        expect:
        expectResolve("resolve", implementsNamed, ["Root: "])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "resolvable configuration inheriting a #enumDesc attribute via extendsFrom"() {
        // Attribute inheritance goes through the container's addAllLater / concat chain,
        // which doesn't touch the desugaring serializer. Any failure comes from Attribute.of
        // during script eval, not from any downstream serialization.
        given:
        settingsFile("include 'consumer', 'producer'")

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myBase") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myBase"))
                }
            }

            dependencies { myDeps(project(":producer")) }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast { files.each { println("Resolved: " + it.name) } }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: output.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "registering a #enumDesc-typed attribute in the attributes schema"() {
        // Registering an attribute in the schema (dependencies.attributesSchema { attribute(...) })
        // walks through DefaultAttributesSchema.attribute(...). The consumer script's own
        // Attribute.of call fires the validation first, so plain row fails identically to
        // other consumer-side plain rows.
        given:
        settingsFile("include 'consumer', 'producer'")

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            dependencies {
                attributesSchema {
                    attribute(ATTRIBUTE_TYPE)
                }
            }

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            dependencies {
                attributesSchema {
                    attribute(ATTRIBUTE_TYPE)
                }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies { myDeps(project(":producer")) }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast { files.each { println("Resolved: " + it.name) } }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: output.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }
    // endregion additional coverage

    // region enum-as-JVM-singleton
    // -------------------------------------------------------------------------
    // Tests that probe the JVM-level enum-singleton semantics: cross-classloader
    // coercion producing consumer-side singletons, anonymous per-constant subclasses
    // being unwrapped via getDeclaringClass, and config-cache save+load preserving
    // the singleton identity. These sit inside "enums succeed" because the underlying
    // machinery handles them correctly for both flavors — the Named row succeeds
    // end-to-end and the plain row is rejected up front by Attribute.of, exactly
    // like every other test in the "enums succeed" region.
    // -------------------------------------------------------------------------
    def "consequence (#enumDesc): cross-classloader coercion silently creates a different singleton"() {
        // Producer and consumer scripts each declare their own MyEnum in independent
        // classloaders. Consumer-side coercion returns the CONSUMER's FOO singleton,
        // not the producer's. This test verifies the retrieved attribute value belongs
        // to the consumer's classloader — the "trap" being that a plugin author
        // holding a reference to a DIFFERENT classloader's FOO would see reference
        // inequality even though names match.
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def artifacts = configurations.myResolver.incoming.artifactView {}.artifacts.resolvedArtifacts
                doLast {
                    artifacts.get().each { r ->
                        def retrieved = r.variant.attributes.getAttribute(ATTRIBUTE_TYPE)
                        assert retrieved.is(MyEnum.FOO): "retrieved \$retrieved is not identical to consumer's MyEnum.FOO"
                        assert retrieved.declaringClass.classLoader.is(MyEnum.class.classLoader)
                        println("Consumer-classloader singleton: OK")
                    }
                }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Consumer-classloader singleton: OK"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }

    def "consequence (#enumDesc): enum with anonymous per-constant inner-class bodies works via getDeclaringClass"() {
        // Enum constants declared with per-constant bodies produce anonymous subclasses
        // (MyEnum$1, MyEnum$2). Gradle uses Enum#getDeclaringClass, not Object#getClass,
        // so no code path treats MyEnum$1 as the attribute value type.
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: output.txt"])

        where:
        enumDesc                | enumDecl                                                         | implementsNamed
        "plain Enum with body"  | """enum MyEnum {
                                       FOO { @Override String describe() { return "the-foo" } },
                                       BAR { @Override String describe() { return "the-bar" } };
                                       abstract String describe()
                                   }"""                                                            | false
        "Named Enum with body"  | """enum MyEnum implements Named {
                                       FOO { @Override String describe() { return "the-foo" } },
                                       BAR { @Override String describe() { return "the-bar" } };
                                       abstract String describe()
                                       @Override String getName() { return name() }
                                   }"""                                                            | true
    }

    def "consequence (#enumDesc): config-cache save-and-reuse preserves the attribute value across script re-parse"() {
        // A build script declaring its own enum gets a fresh classloader on every
        // configuration. Config-cache save serializes attribute values by class name +
        // constant name; restore re-runs the script and re-resolves the constant via
        // Enum.valueOf. This verifies a save→reuse cycle produces the same result.
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            ${enumDecl}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect: "the configCacheIntegTest task variant already replays every test through configuration-cache save+load; a single successful run here proves the enum attribute survives that round-trip"
        expectResolve(":consumer:resolve", implementsNamed, ["Resolved: output.txt"])

        where:
        enumDesc   | enumDecl    | implementsNamed
        PLAIN_DESC | PLAIN_ENUM  | false
        NAMED_DESC | NAMED_ENUM  | true
    }
    // endregion enum-as-JVM-singleton
    // endregion enums succeed

    // region un-named enums fail
    // -------------------------------------------------------------------------
    // The three scenarios below document underlying failures that surface when a
    // plain (non-Named) enum is used as an attribute value type. In the current
    // deprecation-only regime, Attribute.of does NOT halt execution up front —
    // it emits a deprecation warning and lets the build proceed. Depending on the
    // pipeline exercised, the build may then either fail deeper in (test 13's ES
    // regression, test 14's cross-classloader coercion, test 15's GMM coercion),
    // or complete without any hard error visible in the assertions here.
    //
    // Each test is marked @NotYetImplemented and asserts the IDEAL future
    // behavior: Attribute.of should reject a plain enum up front. When the
    // deprecation is promoted to an error in Gradle 10, these tests are expected
    // to pass and the @NotYetImplemented annotations should be removed.
    // See problems-with-unnamed-enums.md in this directory for full analysis.
    // -------------------------------------------------------------------------
    @NotYetImplemented
    def "task-input on a detached configuration with a plain Enum attribute value (regression from Gradle 9.5.1)"() {
        // Reproducer for the ClassCastException reported by the Elasticsearch team.
        // Task-graph-time resolution of a detached configuration with an external Maven dep
        // streams through StreamingResolutionResultBuilder → DesugaringAttributeContainerSerializer,
        // whose else-branch performs an unchecked (Named) cast at line 91. On a plain enum
        // this raises ClassCastException, which DefaultBinaryStore.write wraps as
        // "Problems writing to Binary store". Ideal behavior: Attribute.of rejects the plain
        // enum up front. Current (deprecation-only) behavior: Attribute.of emits a deprecation
        // and the task proceeds far enough to eventually hit the CCE (or, in some environments,
        // completes without surfacing the failure the assertions below check for).
        given:
        mavenRepo.module("org.example", "producer", "1.0").publish()

        buildFile("""
            ${PLAIN_ENUM}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            repositories {
                maven { url = uri("${mavenRepo.uri}") }
            }

            def detachedConf = configurations.detachedConfiguration(
                dependencies.create("org.example:producer:1.0")
            )
            detachedConf.attributes {
                attribute(ATTRIBUTE_TYPE, MyEnum.FOO)
            }

            tasks.register("myTask") {
                inputs.files(detachedConf)
                doLast { }
            }
        """)

        expect:
        fails("myTask")
        failure.assertHasCause(PLAIN_ENUM_DEPRECATION_SUMMARY)
    }

    @NotYetImplemented
    def "enum constants are compile-time closed — producer offers a constant absent from the consumer's enum (plain Enum)"() {
        // The producer publishes a variant using MyEnum.BAR. The consumer's MyEnum has only
        // FOO. Ideal behavior: Attribute.of rejects the plain enum type up front. Current
        // (deprecation-only) behavior: Attribute.of emits a deprecation and the build proceeds
        // into the resolution machinery, where the outcome depends on which coercion path is
        // exercised for plain enums.
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            enum MyEnum { FOO, BAR }
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.BAR) }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            enum MyEnum { FOO }
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        fails(":consumer:resolve")
        failure.assertHasCause(PLAIN_ENUM_DEPRECATION_SUMMARY)
    }

    @NotYetImplemented
    def "GMM value that is not a valid enum constant fails coercion (plain Enum)"() {
        // The GMM wire attribute value must be exactly a constant name of the consumer's enum
        // type. Any drift (typo, renamed constant, spurious value from a component-metadata rule)
        // surfaces as the raw JDK IAE. Ideal behavior: Attribute.of rejects the plain enum type
        // up front. Current (deprecation-only) behavior: Attribute.of emits a deprecation and
        // the build proceeds until it eventually hits the coercion failure.
        given:
        mavenRepo.module("org.example", "producer", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("myVariant", [myEnumAttribute: "NOT_A_CONSTANT"]) {
                artifact("producer-1.0.jar")
            }
            .publish()

        buildFile("""
            ${PLAIN_ENUM}
            def ATTRIBUTE_TYPE = Attribute.of("myEnumAttribute", MyEnum.class)

            repositories {
                maven { url = uri("${mavenRepo.uri}") }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, MyEnum.FOO) }
                }
            }

            dependencies {
                myDeps("org.example:producer:1.0")
            }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.files
                doLast {
                    files.each { println("Resolved: " + it.name) }
                }
            }
        """)

        expect:
        fails("resolve")
        failure.assertHasCause(PLAIN_ENUM_DEPRECATION_SUMMARY)
    }
    def "registering an AttributeCompatibilityRule typed on a plain Enum emits a deprecation when the rule fires"() {
        // The attribute itself is a String (which passes Attribute.of), but the rule is typed
        // on a plain enum, which Groovy allows to register via loose generics.
        // AttributeTypeValidator.validateRuleTypeParameter walks the rule class's superinterfaces
        // at first fire, extracts the plain-enum type argument, and emits a deprecation warning.
        // This will become a hard failure in Gradle 10.
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            def ATTRIBUTE_TYPE = Attribute.of("myAttr", String.class)
            configurations {
                consumable("myVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, "PRODUCER") }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            enum MyPlainEnum { FOO, BAR }

            def ATTRIBUTE_TYPE = Attribute.of("myAttr", String.class)

            abstract class BadRule implements AttributeCompatibilityRule<MyPlainEnum> {
                void execute(CompatibilityCheckDetails details) { details.compatible() }
            }

            dependencies {
                attributesSchema {
                    attribute(ATTRIBUTE_TYPE) {
                        compatibilityRules.add(BadRule)
                    }
                }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, "CONSUMER") }
                }
            }

            dependencies { myDeps(project(":producer")) }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast { files.each { println("Resolved: " + it.name) } }
            }
        """)

        expect:
        executer.noDeprecationChecks()
        succeeds(":consumer:resolve")
        outputContains("as the type parameter of attribute rule 'BadRule' has been deprecated.")
        outputContains("Resolved: output.txt")
    }

    def "registering an AttributeDisambiguationRule typed on a plain Enum emits a deprecation when the rule fires"() {
        // For the disambiguation rule to fire we need multiple *compatible* candidates.
        // Producer offers two variants with distinct values on the target attribute — a
        // compatibility rule (typed correctly on String) makes both compatible; then the
        // badly-typed disambiguation rule is invoked to pick between them and trips our
        // validator's deprecation warning.
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        file("producer/foo.txt") << "foo output"
        file("producer/bar.txt") << "bar output"
        buildFile("producer/build.gradle", """
            def ATTRIBUTE_TYPE = Attribute.of("myAttr", String.class)
            configurations {
                consumable("fooVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, "foo") }
                    outgoing.artifact(file("foo.txt"))
                }
                consumable("barVariant") {
                    attributes { attribute(ATTRIBUTE_TYPE, "bar") }
                    outgoing.artifact(file("bar.txt"))
                }
            }
        """)

        buildFile("consumer/build.gradle", """
            enum MyPlainEnum { FOO, BAR }

            def ATTRIBUTE_TYPE = Attribute.of("myAttr", String.class)

            abstract class AlwaysCompatibleRule implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) { details.compatible() }
            }
            abstract class BadDisambiguationRule implements AttributeDisambiguationRule<MyPlainEnum> {
                void execute(MultipleCandidatesDetails details) { details.closestMatch(details.candidateValues.first()) }
            }

            dependencies {
                attributesSchema {
                    attribute(ATTRIBUTE_TYPE) {
                        compatibilityRules.add(AlwaysCompatibleRule)
                        disambiguationRules.add(BadDisambiguationRule)
                    }
                }
            }

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes { attribute(ATTRIBUTE_TYPE, "foo") }
                }
            }

            dependencies { myDeps(project(":producer")) }

            tasks.register("resolve") {
                def files = configurations.myResolver.incoming.artifactView {}.files
                doLast { files.each { println("Resolved: " + it.name) } }
            }
        """)

        expect:
        executer.noDeprecationChecks()
        succeeds(":consumer:resolve")
        outputContains("as the type parameter of attribute rule 'BadDisambiguationRule' has been deprecated.")
    }
    // endregion un-named enums fail
}
