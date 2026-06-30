/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.dsl.GradleDsl
import spock.lang.Issue

@FluidDependenciesResolveTest
class ResolutionResultApiIntegrationTest extends AbstractDependencyResolutionTest {

    /*
    The ResolutionResult API is also covered by the dependency report integration tests.
     */

    def "selection reasons are described"() {
        given:
        mavenRepo.module("org", "leaf", "1.0").publish()
        mavenRepo.module("org", "leaf", "2.0").publish()
        mavenRepo.module("org", "foo", "0.5").publish()

        mavenRepo.module("org", "foo", "1.0").dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn('org', 'leaf', '2.0').publish()
        mavenRepo.module("org", "baz", "1.0").dependsOn('org', 'foo',  '1.0').publish()

        file("settings.gradle") << "rootProject.name = 'cool-project'"

        file("build.gradle") << """
            version = '5.0'
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            configurations.conf.resolutionStrategy.force 'org:leaf:2.0'
            dependencies {
                conf 'org:foo:0.5', 'org:bar:1.0', 'org:baz:1.0'
            }
            def result = configurations.conf.incoming.resolutionResult
            task resolutionResult {
                doLast {
                    result.allComponents {
                        if(it.id instanceof ModuleComponentIdentifier) {
                            println it.id.module + ":" + it.id.version + " " + it.selectionReason
                        }
                        else if(it.id instanceof ProjectComponentIdentifier) {
                            println it.moduleVersion.name + ":" + it.moduleVersion.version + " " + it.selectionReason
                        }
                    }
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("The ResolutionStrategy.force(Object...) method has been deprecated. This is scheduled to be removed in Gradle 10. Use strict versions instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_resolution_strategy_force")
        run "resolutionResult"

        then:
        output.contains """
cool-project:5.0 root
foo:1.0 between versions 1.0 and 0.5
leaf:2.0 forced
bar:1.0 requested
baz:1.0 requested
"""
    }

    def "resolution result API gives access to dependency reasons in case of conflict"() {
        given:
        mavenRepo.with {
            def leaf1 = module('org.test', 'leaf', '1.0').publish()
            def leaf2 = module('org.test', 'leaf', '1.1').publish()
            module('org.test', 'a', '1.0')
                .dependsOn(leaf1, reason: 'first reason')
                .withModuleMetadata()
                .publish()
            module('org.test', 'b', '1.0')
                .dependsOn(leaf2, reason: 'second reason')
                .withModuleMetadata()
                .publish()
        }

        when:
        file("build.gradle") << """
            configurations {
                conf
            }

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                conf 'org.test:a:1.0'
                conf 'org.test:b:1.0'
            }

            task checkDeps {
                def result = configurations.conf.incoming.resolutionResult
                doLast {
                    result.allComponents {
                        if (it.id instanceof ModuleComponentIdentifier && it.id.module == 'leaf') {
                            def selectionReason = it.selectionReason
                            assert selectionReason.conflictResolution
                            def descriptions = selectionReason.descriptions
                            assert descriptions.size() > 1
                            descriptions.each {
                                println "\$it.cause : \$it.description"
                            }
                            def descriptors = descriptions.findAll { it.cause == ComponentSelectionCause.REQUESTED }
                            assert descriptors.description == ['first reason', 'second reason']
                        }
                    }
                }
            }

        """

        then:
        run "checkDeps"
    }

    def "resolution result API gives access to dependency reasons in case of conflict and selection by rule"() {
        given:
        ResolveTestFixture resolve = new ResolveTestFixture(testDirectory)

        mavenRepo.with {
            module('org.test', 'leaf', '1.0').publish()
            def leaf2 = module('org.test', 'leaf', '1.1').publish()
            module('org.test', 'a', '1.0')
                .dependsOn('org.test', 'leaf', '0.9')
                .withModuleMetadata()
                .publish()
            module('org.test', 'b', '1.0')
                .dependsOn(leaf2, reason: 'second reason')
                .withModuleMetadata()
                .publish()

        }
        settingsFile << """rootProject.name='test'"""
        buildFile << """
            configurations {
                conf {
                    resolutionStrategy {
                        dependencySubstitution {
                            all {
                                if (it.requested instanceof ModuleComponentSelector) {
                                    if (it.requested.module == 'leaf' && it.requested.version == '0.9') {
                                        it.useTarget("org.test:\${it.requested.module}:1.0", "substitute 0.9 with 1.0")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ${resolve.configureProject("conf")}

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                conf 'org.test:a:1.0'
                conf 'org.test:b:1.0'
            }

            tasks.register("checkResolutionResult") {
                def result = configurations.conf.incoming.resolutionResult
                doLast {
                    result.allComponents {
                        if (it.id instanceof ModuleComponentIdentifier && it.id.module == 'leaf') {
                            def selectionReason = it.selectionReason
                            assert selectionReason.conflictResolution
                            def descriptions = selectionReason.descriptions
                            assert descriptions.size() > 1
                            descriptions.each {
                                println "\$it.cause : \$it.description"
                            }
                            def descriptors = descriptions.findAll { it.cause == ComponentSelectionCause.REQUESTED }
                            assert descriptors.description == ['requested', 'second reason']
                        }
                    }
                }
            }
        """

        when:

        run(":checkDeps", ":checkResolutionResult")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.test:a:1.0:runtime') {
                    edge('org.test:leaf:0.9', 'org.test:leaf:1.1')
                        .byConflictResolution("between versions 1.1 and 1.0") // conflict with the version requested by 'b'
                        .byReason('second reason') // this comes from 'b'
                        .selectedByRule("substitute 0.9 with 1.0")
                }
                module('org.test:b:1.0:runtime') {
                    module('org.test:leaf:1.1')
                        .selectedByRule("substitute 0.9 with 1.0")
                        .byConflictResolution("between versions 1.1 and 1.0")
                        .byReason('second reason')
                }
            }
        }
    }

    def "constraint are not mis-showing up as a separate REQUESTED and do not overwrite selection by rule"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        buildFile << """

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf "org:foo:1.0"

                constraints {
                    conf("org:foo:1.0") {
                        version {
                            rejectAll()
                        }
                        if ($useReason) { because("This reason comes from a constraint") }
                    }
                }
            }

            configurations.all {
                resolutionStrategy.dependencySubstitution {
                    substitute(module('org:foo')).because('fix comes from component selection rule').using(module('org:bar:1.0'))
                }
            }

            task checkWithApi {
                def result = configurations.conf.incoming.resolutionResult
                doLast {
                    result.allComponents {
                        if (it.id instanceof ModuleComponentIdentifier) {
                            println "Module \$it.id"
                            it.selectionReason.descriptions.each {
                                println "   \$it.cause : \$it.description"
                            }
                        }
                    }
                }
            }
        """

        when:
        run 'checkWithApi'

        then:
        outputContains("""Module org:bar:1.0
   REQUESTED : requested
   SELECTED_BY_RULE : fix comes from component selection rule
   CONSTRAINT : ${useReason?'This reason comes from a constraint':'constraint'}
""")
        where:
        useReason << [true, false]
    }

    def "direct dependency reasons are not mis-showing up as a separate REQUESTED and do not overwrite selection by rule"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        buildFile << """

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf("org:foo:1.0") {
                    if ($useReason) { because("This is a direct dependency reason") }
                }
            }

            configurations.all {
                resolutionStrategy.dependencySubstitution {
                    substitute(module('org:foo')).because('fix comes from component selection rule').using(module('org:bar:1.0'))
                }
            }

            task checkWithApi {
                def result = configurations.conf.incoming.resolutionResult
                doLast {
                    result.allComponents {
                        if (it.id instanceof ModuleComponentIdentifier) {
                            println "Module \$it.id"
                            it.selectionReason.descriptions.each {
                                println "   \$it.cause : \$it.description"
                            }
                        }
                    }
                }
            }
        """

        when:
        run 'checkWithApi'

        then:
        outputContains("""Module org:bar:1.0
   REQUESTED : ${useReason?'This is a direct dependency reason':'requested'}
   SELECTED_BY_RULE : fix comes from component selection rule
""")
        where:
        useReason << [true, false]
    }

    void "expired cache entry doesn't break reading from cache"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        buildFile << """

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }

            def attr = Attribute.of("myAttribute", String)

            dependencies {
                conf("org:foo:1.0") {
                    because 'first reason' // must have custom reasons to show the problem
                }
                conf("org:bar") {
                    because 'second reason'

                    attributes {
                        attribute(attr, 'val') // make sure attributes are properly serialized and read back
                    }
                }
                constraints {
                    conf("org:bar") {
                        version {
                            require "[0.1, 2.0["
                            prefer "1.0"
                        }
                    }
                }
            }

            task resolveTwice {
                def result = configurations.conf.incoming.resolutionResult
                doLast {
                    result.allComponents {
                        it.selectionReason.descriptions.each {
                           println "\${it.cause} : \${it.description}"
                        }
                    }
                    println 'Waiting for the cache to expire'
                    // see org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.CachedStoreFactory
                    Thread.sleep(800) // must be > cache expiry
                    println 'Read result again'
                    result.allComponents {
                        it.selectionReason.descriptions.each {
                           println "\${it.cause} : \${it.description}"
                        }
                    }
                }
            }
        """
        executer.withArgument('-Dorg.gradle.api.internal.artifacts.ivyservice.resolveengine.store.cacheExpiryMs=500')

        when:
        run 'resolveTwice'

        then:
        noExceptionThrown()
    }

    def "each dependency is associated to its resolved variant"() {
        mavenRepo.module("org", "dep", "1.0").publish()
        mavenRepo.module("com", "foo", "1.0").publish()
        mavenRepo.module("com", "bar", "1.0").publish()
        mavenRepo.module("com", "baz", "1.0").publish()
        settingsKotlinFile << """
            rootProject.name = "root"
            include("lib")
            include("tool")
            dependencyResolutionManagement {
                ${mavenTestRepository(GradleDsl.KOTLIN)}
            }
        """

        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":lib"))
                testImplementation(testFixtures(project(":tool")))
                testImplementation(testFixtures(project(":tool"))) // intentional duplication
            }
        """

        file("lib/build.gradle.kts") << """
            plugins {
                id("java-library")
            }

            dependencies {
                api("org:dep:1.0")
            }
        """

        file("tool/build.gradle.kts") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }

            dependencies {
                api("com:baz:1.0")
                testFixturesApi("com:foo:1.0")
                testFixturesImplementation("com:bar:1.0")
            }
        """

        buildKotlinFile << """
            ${graphTraverserTask}
            ${variantPrinterCallback}

            tasks.register<TraverseTask>("traverseCompile") {
                resolutionResult = configurations.testCompileClasspath.map { it.incoming.resolutionResult }
                callback = printVariant
                outputFile = layout.buildDirectory.file("traverse-compile.txt")
            }

            tasks.register<TraverseTask>("traverseRuntime") {
                resolutionResult = configurations.testRuntimeClasspath.map { it.incoming.resolutionResult }
                callback = printVariant
                outputFile = layout.buildDirectory.file("traverse-runtime.txt")
            }
        """

        when:
        succeeds("traverseCompile", "traverseRuntime")

        then:
        file("build/traverse-compile.txt").text.trim() == """
root project 'root' (testCompileClasspath)
   project ':lib' (apiElements)
   project ':tool' (testFixturesApiElements)
project ':lib' (apiElements)
   org:dep:1.0 (compile)
project ':tool' (testFixturesApiElements)
   project ':tool' (apiElements)
   com:foo:1.0 (compile)
project ':tool' (apiElements)
   com:baz:1.0 (compile)
""".trim()

        and:
        file("build/traverse-runtime.txt").text.trim() == """
root project 'root' (testRuntimeClasspath)
   project ':lib' (runtimeElements)
   project ':tool' (testFixturesRuntimeElements)
project ':lib' (runtimeElements)
   org:dep:1.0 (runtime)
project ':tool' (testFixturesRuntimeElements)
   project ':tool' (runtimeElements)
   com:foo:1.0 (runtime)
   com:bar:1.0 (runtime)
project ':tool' (runtimeElements)
   com:baz:1.0 (runtime)
""".trim()
    }

    def "requested dependency attributes are reported on dependency result as desugared attributes"() {
        settingsFile << "include 'platform'"
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(platform(project(":platform")))
            }

            task checkDependencyAttributes {
                def rootComponent = configurations.compileClasspath.incoming.resolutionResult.rootComponent
                doLast {
                    rootComponent.get().dependencies.each {
                        def desugaredCategory = Attribute.of("org.gradle.category", String)
                        assert it.requested.attributes.getAttribute(desugaredCategory) == 'platform'
                    }
                }
            }
        """

        file("platform/build.gradle") << """
            plugins {
                id("java-platform")
            }
        """

        expect:
        succeeds 'checkDependencyAttributes'
    }

    def "reports duplicated dependencies in all variants"() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'baz', '1.0').publish()
        mavenRepo.module('org', 'gaz', '1.0').publish()

        settingsKotlinFile << """
            rootProject.name = "root"
            include("producer")
            dependencyResolutionManagement {
                ${mavenTestRepository(GradleDsl.KOTLIN)}
            }
        """

        file("producer/build.gradle.kts") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }
            dependencies {
                testFixturesApi("org:foo:1.0")
                testFixturesImplementation("org:bar:1.0")
                testFixturesImplementation("org:baz:1.0")

                api("org:baz:1.0")
                implementation("org:gaz:1.0")
            }
        """

        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":producer"))
                testImplementation(testFixtures(project(":producer")))
            }
        """

        buildKotlinFile << """
            ${graphTraverserTask}
            ${variantPrinterCallback}

            tasks.register<TraverseTask>("traverseCompile") {
                resolutionResult = configurations.testCompileClasspath.map { it.incoming.resolutionResult }
                callback = printVariant
                outputFile = layout.buildDirectory.file("traverse-compile.txt")
            }

            tasks.register<TraverseTask>("traverseRuntime") {
                resolutionResult = configurations.testRuntimeClasspath.map { it.incoming.resolutionResult }
                callback = printVariant
                outputFile = layout.buildDirectory.file("traverse-runtime.txt")
            }
        """

        when: "baz should appear in both apiElements and testFixturesRuntimeElements"
        succeeds("traverseCompile", "traverseRuntime")

        then:
        file("build/traverse-compile.txt").text.trim() == """
root project 'root' (testCompileClasspath)
   project ':producer' (apiElements)
   project ':producer' (testFixturesApiElements)
project ':producer' (apiElements)
   org:baz:1.0 (compile)
project ':producer' (testFixturesApiElements)
   project ':producer' (apiElements)
   org:foo:1.0 (compile)
""".trim()

        and:
        file("build/traverse-runtime.txt").text.trim() == """
root project 'root' (testRuntimeClasspath)
   project ':producer' (runtimeElements)
   project ':producer' (testFixturesRuntimeElements)
project ':producer' (runtimeElements)
   org:baz:1.0 (runtime)
   org:gaz:1.0 (runtime)
project ':producer' (testFixturesRuntimeElements)
   project ':producer' (runtimeElements)
   org:foo:1.0 (runtime)
   org:bar:1.0 (runtime)
   org:baz:1.0 (runtime)
""".trim()
    }

    def "reports if we try to get dependencies from a different variant"() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        settingsFile << """
            rootProject.name = 'test'
            include 'producer'
            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """

        file("producer/build.gradle") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }
            dependencies {
                testFixturesApi('org:foo:1.0')
            }
            """
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(':producer'))
                testImplementation(testFixtures(project(':producer')))
            }

            task resolve {
                def result = configurations.testCompileClasspath.incoming.resolutionResult
                doLast {
                    def childComponent = result.allComponents.find { it.toString() == "project ':producer'" }
                    def childVariant = childComponent.variants[0]
                    // try to get dependencies for child variant on the wrong component
                    println(result.rootComponent.get().getDependenciesForVariant(childVariant))
                }
            }
        """

        when:
        fails 'resolve'

        then:
        failure.assertHasCause("Variant 'apiElements' doesn't belong to resolved component 'root project 'test''. There's no resolved variant with the same name. Most likely you are using a variant from another component to get the dependencies of this component.")
    }

    @Issue("https://github.com/gradle/gradle/issues/12643")
    def "resolved variant of a selected node shouldn't be null"() {
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            configurations.all {
                resolutionStrategy.capabilitiesResolution.withCapability('com.google.collections:google-collections') {
                    selectHighestVersion()
                }
            }
            dependencies {
                implementation 'com.google.guava:guava:28.1-jre'
                implementation 'com.google.collections:google-collections:1.0'
                components {
                    withModule('com.google.guava:guava') {
                        allVariants {
                            withCapabilities {
                               addCapability('com.google.collections', 'google-collections', id.version)
                            }
                        }
                    }
                }
            }

            task resolve {
                def result = configurations.compileClasspath.incoming.resolutionResult
                doLast {
                    result.allDependencies {
                        assert it instanceof ResolvedDependencyResult
                        assert it.resolvedVariant != null
                    }
                }
            }
        """

        expect:
        succeeds 'resolve'
    }

    private static String getVariantPrinterCallback() {
        """
            val printVariant: (ResolvedComponentResult, ResolvedVariantResult) -> String = { component, variant ->
                val deps = component.getDependenciesForVariant(variant)
                if (deps.isEmpty()) {
                    ""
                } else {
                    buildString {
                        appendLine("\$component (\${variant.displayName})")
                        deps.forEach { dep ->
                            when (dep) {
                                is ResolvedDependencyResult -> appendLine("   \${dep.selected} (\${dep.resolvedVariant.displayName})")
                                else -> appendLine("   \$dep (unresolved)")
                            }
                        }
                    }
                }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/26334")
    def "resolution result does not report duplicate variants for the same module reachable through different paths"() {
        settingsFile << """
            include "producer"
            include "transitive"
        """
        file("producer/build.gradle") << """
            configurations {
                dependencyScope("runtimeOnly")
                dependencyScope("implementation")
                consumable("runtimeElements") {
                    attributes.attribute(Attribute.of("attr1", Named), objects.named(Named,"value"))
                    extendsFrom(runtimeOnly, implementation)
                }
            }
            dependencies {
              runtimeOnly(project(":transitive"))
              implementation(project(":transitive"))
            }
        """
        file("transitive/build.gradle") << """
            configurations {
                consumable("runtimeElements") {
                    attributes.attribute(Attribute.of("attr1", Named), objects.named(Named,"value"))
                }
            }
        """
        buildFile << """
            configurations {
                dependencyScope("implementation")
                resolvable("runtimeClasspath") {
                    attributes.attribute(Attribute.of("attr1", Named), objects.named(Named,"value"))
                    extendsFrom(implementation)
                }
            }
            dependencies {
                implementation project(':producer')
            }

            task resolve {
                def rootComponent = configurations.runtimeClasspath.incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootComponent.get()
                    assert root.dependencies.size() == 1
                    def producer = root.dependencies[0]
                    def producerDependencies = producer.selected.dependencies
                    def producerDependenciesForVariant = producer.selected.getDependenciesForVariant(producer.resolvedVariant)
                    assert producerDependencies.size() == 1 // producer has only one variant dependency on transitive
                    assert producerDependencies.size() == producerDependenciesForVariant.size()
                    assert producerDependencies.containsAll(producerDependenciesForVariant)
                }
            }
        """

        expect:
        succeeds("resolve")

    }
    def "resolution result does not realize artifact tasks"() {
        settingsFile << "include 'producer'"
        file("producer/build.gradle") << """
            plugins {
                id("base")
            }

            def fooTask = tasks.register("foo", Zip) {
                throw new RuntimeException("Realized artifact task")
            }

            configurations {
                consumable("conf") {
                    outgoing {
                        artifact(fooTask)
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, "cat"))
                    }
                }
            }
        """

        buildFile << """
            configurations {
                conf {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, "cat"))
                    }
                }
            }

            dependencies {
                conf project(":producer")
            }

            task resolve {
                def rootComponent = configurations.conf.incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootComponent.get()
                    assert root.dependencies.size() == 1
                    def producer = root.dependencies[0].selected
                    assert producer.variants.first().displayName == "conf"
                }
            }

            task selectArtifacts {
                def files = configurations.conf.incoming.files
                doLast {
                    println files.files
                }
            }
        """

        expect:
        succeeds("resolve")

        and:
        fails("selectArtifacts")
        failure.assertHasCause("Realized artifact task")
    }

    def "exposes root variant"() {
        mavenRepo.module("org", "foo").publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                ${hasDependencies ? 'implementation("org:foo:1.0")' : "" }
            }

            task resolve {
                def rootComponent = configurations.runtimeClasspath.incoming.resolutionResult.rootComponent
                def rootVariant = configurations.runtimeClasspath.incoming.resolutionResult.rootVariant
                doLast {
                    def componentRootVariant = rootComponent.get().variants.find { it.displayName == "runtimeClasspath" }
                    assert rootVariant.get() == componentRootVariant
                }
            }
        """

        expect:
        succeeds("resolve")

        where:
        hasDependencies << [true, false]
    }

    def "handles graphs with cycles"() {
        settingsFile << """
            include 'other'
        """
        file("other/build.gradle") << """
            configurations {
                dependencyScope("implementation")
                consumable("runtimeElements") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "cat"))
                    }
                }
            }

            dependencies {
                implementation(project(":"))
            }
        """

        buildFile << """
            configurations {
                dependencyScope("implementation")
                consumable("runtimeElements") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "cat"))
                    }
                }
                resolvable("runtimeClasspath") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "cat"))
                    }
                }
            }

            dependencies {
                implementation(project(":other"))
            }

            task resolve {
                def rootVariantProvider = configurations.runtimeClasspath.incoming.resolutionResult.rootVariant
                def rootComponentProvider = configurations.runtimeClasspath.incoming.resolutionResult.rootComponent
                doLast {
                    def rootVariant = rootVariantProvider.get()
                    def rootComponent = rootComponentProvider.get()

                    def rootDependencies = rootComponent.getDependenciesForVariant(rootVariant)
                    assert rootComponent.dependencies.size() == 1
                    assert rootDependencies.size() == 1
                    assert rootDependencies[0] == rootComponent.dependencies[0]

                    def rootDependency = rootDependencies[0]
                    assert rootDependency instanceof ResolvedDependencyResult

                    def otherComponent = rootDependency.selected
                    def otherVariant = rootDependency.resolvedVariant

                    def otherDependencies = otherComponent.getDependenciesForVariant(otherVariant)
                    assert otherComponent.dependencies.size() == 1
                    assert otherDependencies.size() == 1
                    assert otherDependencies[0] == otherComponent.dependencies[0]

                    def otherDependency = otherDependencies[0]
                    assert otherDependency instanceof ResolvedDependencyResult
                    assert otherDependency.selected == rootComponent

                    def runtimeElements = otherDependency.resolvedVariant
                    def runtimeElementsDependencies = rootComponent.getDependenciesForVariant(runtimeElements)

                    assert runtimeElementsDependencies.size() == 1
                    assert runtimeElementsDependencies[0].selected == otherComponent
                    assert runtimeElementsDependencies[0].resolvedVariant == otherVariant
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "resolution result is a valid task input"() {
        mavenRepo.module("org", "foo")
            .dependsOn(mavenRepo.module("org", "bar").publish())
            .publish()

        settingsKotlinFile << """
            rootProject.name = "root"
            include("other")
        """

        file("other/build.gradle.kts") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }

            group = "org"
            version = "1.0"
        """

        buildKotlinFile << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }

            group = "org"
            version = "1.0"

            ${mavenTestRepository(GradleDsl.KOTLIN)}

            dependencies {
                implementation("org:foo:1.0")
                implementation(project(":other"))
                testImplementation(testFixtures(project(":other")))
            }

            ${graphTraverserTask}
            tasks.register<TraverseTask>("traverse") {
                resolutionResult = configurations.testRuntimeClasspath.map { it.incoming.resolutionResult }
                callback = { _, variant ->
                    val displayName = when (val owner = variant.owner) {
                        is ProjectComponentIdentifier -> "\${owner.buildTreePath}:\${variant.displayName}"
                        is ModuleComponentIdentifier -> "\${owner.displayName}:\${variant.displayName}"
                        else -> error("Unknown component type")
                    }
                    "\$displayName\\n"
                }
                outputFile = layout.buildDirectory.file("traverse.txt")
            }
        """

        def expected = """
::testRuntimeClasspath
org:foo:1.0:runtime
:other:runtimeElements
::testFixturesRuntimeElements
:other:testFixturesRuntimeElements
org:bar:1.0:runtime
::runtimeElements
""".trim()

        when:
        succeeds("traverse")

        then:
        file("build/traverse.txt").text.trim() == expected

        when:
        succeeds("traverse")

        then:
        result.assertAllTasksSkipped()
        file("build/traverse.txt").text.trim() == expected
    }

    private static String getGraphTraverserTask() {
        """
            abstract class TraverseTask : DefaultTask() {

                @get:Input
                abstract val resolutionResult: Property<ResolutionResult>

                @get:Input
                abstract val callback: Property<(ResolvedComponentResult, ResolvedVariantResult) -> String>

                @get:OutputFile
                abstract val outputFile: RegularFileProperty

                @TaskAction
                fun traverse() {
                    val result = resolutionResult.get()
                    val cb = callback.get()
                    val output = StringBuilder()
                    traverseGraphVariants(result.rootComponent.get(), result.rootVariant.get()) { component, variant ->
                        output.append(cb(component, variant))
                    }
                    outputFile.get().asFile.writeText(output.toString())
                }

                fun traverseGraphVariants(
                    rootComponent: ResolvedComponentResult,
                    rootVariant: ResolvedVariantResult,
                    callback: (ResolvedComponentResult, ResolvedVariantResult) -> Unit
                ) {
                    val seen = mutableSetOf(rootVariant)
                    val queue = ArrayDeque(listOf(rootVariant to rootComponent))

                    while (queue.isNotEmpty()) {
                        val (variant, component) = queue.removeFirst()

                        callback(component, variant)

                        // Traverse this variant's dependencies
                        component.getDependenciesForVariant(variant).forEach { dependency ->
                            val resolved = when (dependency) {
                                is ResolvedDependencyResult -> dependency
                                is UnresolvedDependencyResult -> throw dependency.failure
                                else -> throw AssertionError("Unknown dependency type: \$dependency")
                            }

                            if (!resolved.isConstraint && seen.add(resolved.resolvedVariant)) {
                                queue.add(resolved.resolvedVariant to resolved.selected)
                            }
                        }
                    }
                }
            }
        """
    }

    def "attributes on root variant can be requested using Stringly or strongly-typed values"() {
        settingsFile << """
            include("producer")
        """

        file("producer/build.gradle") << """
            plugins {
                id("java-library")
            }
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation project(":producer")
            }

            task resolve {
                def root = configurations.runtimeClasspath.incoming.resolutionResult.rootVariant

                doLast {
                    def usage = root.get().attributes.getAttribute(Usage.USAGE_ATTRIBUTE)
                    assert Usage.class.isAssignableFrom(usage.class)
                    assert usage.name == "java-runtime"

                    def usageAsString = root.get().attributes.getAttribute(Attribute.of(Usage.USAGE_ATTRIBUTE.name, String.class))
                    assert usageAsString == "java-runtime"
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "capabilities on variant always return same instance"() {
        mavenRepo.module("org", "foo", "1.0").publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org:foo:1.0")
            }

            tasks.register("resolve") {
                def rootComponent = configurations.runtimeClasspath.incoming.resolutionResult.rootComponent
                doLast {
                    def variant = rootComponent.get().dependencies.first().resolvedVariant
                    assert variant.capabilities.is(variant.capabilities)
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
