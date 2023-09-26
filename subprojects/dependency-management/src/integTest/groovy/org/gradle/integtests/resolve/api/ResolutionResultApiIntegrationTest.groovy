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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

@FluidDependenciesResolveTest
class ResolutionResultApiIntegrationTest extends AbstractDependencyResolutionTest {
    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, 'conf')

    /*
    The ResolutionResult API is also covered by the dependency report integration tests.
     */

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
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
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            configurations.conf.resolutionStrategy.force 'org:leaf:2.0'
            dependencies {
                conf 'org:foo:0.5', 'org:bar:1.0', 'org:baz:1.0'
            }
            task resolutionResult {
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
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

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
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
               maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                conf 'org.test:a:1.0'
                conf 'org.test:b:1.0'
            }

            task checkDeps {
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
                    result.allComponents {
                        if (it.id instanceof ModuleComponentIdentifier && it.id.module == 'leaf') {
                            def selectionReason = it.selectionReason
                            assert selectionReason.conflictResolution
                            def descriptions = selectionReason.descriptions.reverse()
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

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    def "resolution result API gives access to dependency reasons in case of conflict and selection by rule"() {
        given:
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
        file("build.gradle") << """
            configurations {
                conf {
                    resolutionStrategy {
                        dependencySubstitution {
                            all {
                                if (it.requested instanceof ModuleComponentSelector) {
                                    if (it.requested.module == 'leaf' && it.requested.version == '0.9') {
                                        it.useTarget("substitute 0.9 with 1.0", group: 'org.test', name: it.requested.module, version: '1.0')
                                    }
                                }
                            }
                        }
                    }
                }
            }

            repositories {
               maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                conf 'org.test:a:1.0'
                conf 'org.test:b:1.0'

            }
        """
        resolve.prepare()
        buildFile << """
            checkDeps {
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
                    result.allComponents {
                        if (it.id instanceof ModuleComponentIdentifier && it.id.module == 'leaf') {
                            def selectionReason = it.selectionReason
                            assert selectionReason.conflictResolution
                            def descriptions = selectionReason.descriptions.reverse()
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

        run "checkDeps"

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

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    def "constraint are not mis-showing up as a separate REQUESTED and do not overwrite selection by rule"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        buildFile << """

            repositories {
               maven { url "${mavenRepo.uri}" }
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
                resolutionStrategy.eachDependency {
                    if (requested.name == 'foo') {
                        because("fix comes from component selection rule").useTarget("org:bar:1.0")
                    }
                }
            }

            task checkWithApi {
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
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

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    def "direct dependency reasons are not mis-showing up as a separate REQUESTED and do not overwrite selection by rule"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        buildFile << """

            repositories {
               maven { url "${mavenRepo.uri}" }
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
                resolutionStrategy.eachDependency {
                    if (requested.name == 'foo') {
                        because("fix comes from component selection rule").useTarget("org:bar:1.0")
                    }
                }
            }

            task checkWithApi {
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
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

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    void "expired cache entry doesn't break reading from cache"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        buildFile << """

            repositories {
               maven { url "${mavenRepo.uri}" }
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
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
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

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    def "each dependency is associated to its resolved variant"() {
        mavenRepo.module("org", "dep", "1.0").publish()
        mavenRepo.module("com", "foo", "1.0").publish()
        mavenRepo.module("com", "bar", "1.0").publish()
        mavenRepo.module("com", "baz", "1.0").publish()
        settingsFile << """
            include 'lib', 'tool'
        """
        buildFile << """
            allprojects {
               repositories {
                  maven { url "${mavenRepo.uri}" }
               }

                apply plugin: 'java-library'
            }

            project(":lib") {
                dependencies {
                   api "org:dep:1.0"
                }
            }

            project(":tool") {
                apply plugin: 'java-test-fixtures'
                dependencies {
                    api "com:baz:1.0"
                    testFixturesApi "com:foo:1.0"
                    testFixturesImplementation "com:bar:1.0"
                }
            }

            dependencies {
                implementation(project(":lib"))
                testImplementation(testFixtures(project(":tool")))
                testImplementation(testFixtures(project(":tool"))) // intentional duplication
            }
        """
        withResolutionResultDumper("testCompileClasspath", "testRuntimeClasspath")

        when:
        succeeds 'resolve'

        then:
        outputContains """
testCompileClasspath
   project :lib (apiElements)
      org:dep:1.0 (compile)
   project :tool (testFixturesApiElements)
      project :tool (apiElements)
         com:baz:1.0 (compile)
      com:foo:1.0 (compile)
"""

        and:
        outputContains """testRuntimeClasspath
   project :lib (runtimeElements)
      org:dep:1.0 (runtime)
   project :tool (testFixturesRuntimeElements)
      project :tool (runtimeElements)
         com:baz:1.0 (runtime)
      com:foo:1.0 (runtime)
      com:bar:1.0 (runtime)
"""
    }

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    def "requested dependency attributes are reported on dependency result as desugared attributes"() {
        settingsFile << "include 'platform'"
        buildFile << """
            project(":platform") {
                apply plugin: 'java-platform'
            }

            apply plugin: 'java-library'

            dependencies {
                implementation(platform(project(":platform")))
            }

            task checkDependencyAttributes {
                def compileClasspath = configurations.compileClasspath
                doLast {
                    compileClasspath.incoming.resolutionResult.root.dependencies.each {
                        def desugaredCategory = Attribute.of("org.gradle.category", String)
                        assert it.requested.attributes.getAttribute(desugaredCategory) == 'platform'
                    }
                }
            }
        """

        expect:
        succeeds 'checkDependencyAttributes'
    }

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    def "reports duplicated dependencies in all variants"() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'baz', '1.0').publish()
        mavenRepo.module('org', 'gaz', '1.0').publish()

        file("producer/build.gradle") << """
            plugins {
              id 'java-library'
              id 'java-test-fixtures'
            }
            dependencies {
              testFixturesApi('org:foo:1.0')
              testFixturesImplementation('org:bar:1.0')
              testFixturesImplementation('org:baz:1.0')

              api('org:baz:1.0')
              implementation('org:gaz:1.0')
            }
            """
                    buildFile << """
            plugins {
              id 'java-library'
            }

            allprojects {
               repositories {
                  maven { url "${mavenRepo.uri}" }
               }
            }

            dependencies {
              implementation(project(':producer'))
              testImplementation(testFixtures(project(':producer')))
            }
        """
        settingsFile << """
            include 'producer'
        """

        withResolutionResultDumper("testCompileClasspath", "testRuntimeClasspath")

        when: "baz should appear in both apiElements and testFixturesRuntimeElements"
        succeeds 'resolve'

        then:
        outputContains("""
testCompileClasspath
   project :producer (apiElements)
      org:baz:1.0 (compile)
   project :producer (testFixturesApiElements)
      project :producer (apiElements)
      org:foo:1.0 (compile)
""")

        and:
        outputContains("""
testRuntimeClasspath
   project :producer (runtimeElements)
      org:baz:1.0 (runtime)
      org:gaz:1.0 (runtime)
   project :producer (testFixturesRuntimeElements)
      project :producer (runtimeElements)
      org:foo:1.0 (runtime)
      org:bar:1.0 (runtime)
      org:baz:1.0 (runtime)
""")
    }

    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    def "reports if we try to get dependencies from a different variant"() {
        mavenRepo.module('org', 'foo', '1.0').publish()

        file("producer/build.gradle") << """
            plugins {
              id 'java-library'
              id 'java-test-fixtures'
            }
            dependencies {
              testFixturesApi('org:foo:1.0')
            }
            """
        buildFile << """
            plugins {
              id 'java-library'
            }

            allprojects {
               repositories {
                  maven { url "${mavenRepo.uri}" }
               }
            }

            dependencies {
              implementation(project(':producer'))
              testImplementation(testFixtures(project(':producer')))
            }

            task resolve {
                def testCompileClasspath = configurations.testCompileClasspath
                doLast {
                    def result = testCompileClasspath.incoming.resolutionResult
                    def rootComponent = result.root
                    def childComponent = result.allComponents.find { it.toString() == 'project :producer' }
                    def childVariant = childComponent.variants[0]
                    // try to get dependencies for child variant on the wrong component
                    println(rootComponent.getDependenciesForVariant(childVariant))
                }
            }
        """
        settingsFile << """
            include 'producer'
        """

        when:
        fails 'resolve'

        then:
        failure.assertHasCause("Variant 'apiElements' doesn't belong to resolved component 'project :'. There's no resolved variant with the same name. Most likely you are using a variant from another component to get the dependencies of this component.")
    }

    @Issue("https://github.com/gradle/gradle/issues/12643")
    @ToBeFixedForConfigurationCache(because = "task exercises the resolution result API")
    def "resolved variant of a selected node shouldn't be null"() {
        buildFile << """
        apply plugin: 'java-library'

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
            def compileClasspath = configurations.compileClasspath
            doLast {
                def result = compileClasspath.incoming.resolutionResult
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

    private void withResolutionResultDumper(String... configurations) {
        def confCapture = configurations.collect( configuration ->
            "def $configuration = configurations.$configuration"
        )

        def confList = configurations.collect { configuration ->
            """
                // dump variant dependencies
                def result_$configuration = ${configuration}.incoming.resolutionResult
                dump("$configuration", result_${configuration}.root, null, 0)

                // check that configuration attributes are visible and desugared
                def consumerAttributes_$configuration = configurations.${configuration}.attributes
                assert result_${configuration}.requestedAttributes.keySet().size() == consumerAttributes_${configuration}.keySet().size()
                consumerAttributes_${configuration}.keySet().each {
                    println "Checking \$it of type \$it.type"
                    def desugared = Attribute.of(it.name, String)
                    assert result_${configuration}.requestedAttributes.getAttribute(desugared) == consumerAttributes_${configuration}.getAttribute(it).toString()
                }
            """
        }
        buildFile << """

            task resolve {
                ${confCapture.join('\n')}
                doLast {
                    { -> ${confList.join('\n')} }()
                    println()
                    println 'Waiting for the cache to expire'
                    // see org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.CachedStoreFactory
                    Thread.sleep(800) // must be > cache expiry
                    println 'Read result again to make sure serialization state is ok'
                    println();
                    { -> ${confList.join('\n')} }()
                }
            }

            void dump(String root, ResolvedComponentResult result, ResolvedVariantResult variant, int depth, Set visited = []) {
                if (visited.add([result, variant])) {
                    if (variant == null) {
                        println(root)
                    }
                    def dependencies = variant == null ? result.dependencies : result.getDependenciesForVariant(variant)
                    depth++
                    dependencies.each {
                        if (it instanceof ResolvedDependencyResult) {
                            def resolvedVariant = it.resolvedVariant
                            def selected = it.selected
                            println("   " * depth + "\$selected (\$resolvedVariant)")
                            dump(root, selected, resolvedVariant, depth, visited)
                        } else {
                            println("   " * depth + "\$it (unresolved)")
                        }
                    }
                }
            }
"""
    }

    @Issue("https://github.com/gradle/gradle/issues/26334")
    def "resolution result does not report duplicate variants for the same module reachable through different paths"() {
        settingsFile << """
            include "producer", "transitive"
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
}
