/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.publish.ivy

import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.ivy.IvyJavaModule
import spock.lang.Issue

class IvyPublishResolvedVersionsJavaLibraryIntegTest extends AbstractIvyPublishResolvedVersionsJavaIntegTest {
    @ToBeFixedForConfigurationCache
    def "can publish java-library with dependencies (#apiMapping, #runtimeMapping)"() {
        given:
        javaLibrary(ivyRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "foo", "1.1")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:foo:[1.0,1.0]"
                runtimeOnly "org.test:bar:+"
                runtimeOnly "org.test:foo:+"
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            $apiMapping
                            $runtimeMapping
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org.test:foo:1.1") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        javaLibrary.parsedIvy.assertConfigurationDependsOn('compile', "org.test:foo:1.0")
        javaLibrary.parsedIvy.assertConfigurationDependsOn('runtime', 'org.test:bar:1.1', "org.test:foo:1.1")

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
        }

        where:
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')"), apiUsingUsage("fromResolutionOf(project.configurations.compileClasspath)")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)")],
        ].combinations() + [[allVariants(), noop()]])
    }

    @ToBeFixedForConfigurationCache
    def "can publish java-library with dependencies (#runtimeMapping, #apiMapping)"() {
        given:
        javaLibrary(ivyRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "foo", "1.1")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:foo:[1.0,1.0]"
                runtimeOnly "org.test:bar:+"
                runtimeOnly "org.test:foo:+"
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            $runtimeMapping
                            $apiMapping
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org.test:foo:1.1") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        javaLibrary.parsedIvy.assertConfigurationDependsOn('compile', "org.test:foo:1.0")
        javaLibrary.parsedIvy.assertConfigurationDependsOn('runtime', 'org.test:bar:1.1', "org.test:foo:1.1")

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
        }

        where:
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')"), apiUsingUsage("fromResolutionOf(project.configurations.compileClasspath)")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)")],
        ].combinations() + [[allVariants(), noop()]])
    }
}

class IvyPublishResolvedVersionsIntegTest extends AbstractIvyPublishResolvedVersionsJavaIntegTest {
    /**
     * This use case corresponds to the cases where the published versions should be different
     * from the versions published using the default configurations (compileClasspath, runtimeClasspath).
     * This can be the case if there are multiple compile classpath and one should be preferred for publication,
     * or when the component is not a Java library and we don't have a default.
     */
    @ToBeFixedForConfigurationCache
    def "can publish resolved versions from a different configuration (#config)"() {
        given:
        javaLibrary(ivyRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            configurations {
                extra.extendsFrom(api)
            }
            dependencies {
                api "org.test:foo:1.0"
                implementation "org.test:bar:1.0"
                extra "org.test:bar:1.1"
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            ${runtimeUsingUsage(config)}
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org.test:foo:1.0") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        javaLibrary.parsedIvy.assertConfigurationDependsOn('compile', 'org.test:foo:1.0')
        javaLibrary.parsedIvy.assertConfigurationDependsOn('runtime', 'org.test:foo:1.0', 'org.test:bar:1.1')

        and:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        where:
        config << [
            "fromResolutionOf('extra')",
            "fromResolutionOf(project.configurations.extra)"
        ]
    }

    @ToBeFixedForConfigurationCache
    def "can publish resolved versions from dependency constraints (#apiMapping, #runtimeMapping)"() {
        javaLibrary(ivyRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        given:
        createBuildScripts("""
            dependencies {
                constraints {
                    api "org.test:bar:+"
                }
                api "org.test:foo:1.0"
                implementation "org.test:bar"
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            $apiMapping
                            $runtimeMapping
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            constraint("org.test:bar:1.1") {
                exists()
            }
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        def dependencies = javaLibrary.parsedIvy.dependencies
        dependencies.get("org.test:foo:1.0").with {
            assert it.org == 'org.test'
            assert it.module == 'foo'
            assert it.revision == '1.0'
            assert it.confs == ['compile->default', 'runtime->default'] as Set
        }
        dependencies.get("org.test:bar:1.1").with {
            assert it.org == 'org.test'
            assert it.module == 'bar'
            assert it.revision == '1.1'
            assert it.confs == ['runtime->default'] as Set
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            constraint("org.test:bar:1.1") {
                exists()
            }
            dependency("org.test:foo:1.0") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        where:
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')")]
        ].combinations() + [[allVariants(), noop()]])
    }
}

class IvyPublishResolvedVersionsSubstitutionIntegTest extends AbstractIvyPublishResolvedVersionsJavaIntegTest {
    // This is a weird test case, because why would you have a substitution rule
    // for a first level dependency? However it may be that you implicitly get a
    // substitution rule (via a plugin for example) that you are not aware of.
    // Ideally we should warn when such things happen (linting).
    @ToBeFixedForConfigurationCache
    def "substituted dependencies are also substituted in the generated Ivy file"() {
        javaLibrary(ivyRepo.module("org", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(ivyRepo.module("org", "bar", "1.0"))
            .dependsOn("org", "baz", "1.0")
            .withModuleMetadata()
            .publish()
        javaLibrary(ivyRepo.module("org", "baz", "1.0")).withModuleMetadata().publish()

        given:
        createBuildScripts("""
            dependencies {
                implementation 'org:foo:1.0'
                implementation 'org:bar:1.0'
            }

            $substitution

            publishing {
                publications {
                    maven(IvyPublication) {
                        from components.java
                        versionMapping {
                            ${apiUsingUsage()}
                            ${runtimeUsingUsage()}
                        }
                    }

                }
            }
        """)

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedIvy.assertConfigurationDependsOn('runtime', 'org:baz:1.0', 'org:bar:1.0')
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org", "baz", "1.0")
            dependency("org", "bar", "1.0")
            noMoreDependencies()
        }

        where:
        substitution << [
            """
            dependencies {
                modules {
                    module("org:foo") {
                        replacedBy("org:baz")
                    }
                }
            }""",
            """
            configurations.all {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.name == 'foo') {
                        details.useTarget("org:baz:1.0")
                    }
                }
            }
            """,
            """
            configurations.all {
                resolutionStrategy.dependencySubstitution {
                    substitute(module('org:foo')).using(module('org:baz:1.0'))
                }
            }
            """
        ]
    }

    @ToBeFixedForConfigurationCache
    def "can substitute with a project dependency"() {
        given:
        settingsFile << """
            include 'lib'
        """
        createBuildScripts("""
            dependencies {
                implementation 'org:foo:1.0'
            }

            configurations.all {
                resolutionStrategy.dependencySubstitution {
                    substitute(module('org:foo')) using(project(':lib'))
                }
            }

            publishing {
                publications {
                    maven(IvyPublication) {
                        from components.java
                        versionMapping {
                            ${apiUsingUsage()}
                            ${runtimeUsingUsage()}
                        }
                    }

                }
            }
        """)

        file("lib/build.gradle") << """
            apply plugin: 'java-library'

            group = 'com.acme'
            version = '1.45'
        """

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedIvy.assertConfigurationDependsOn("runtime", "com.acme:lib:1.45")
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("com.acme", "lib", "1.45")
            noMoreDependencies()
        }
    }

    @ToBeFixedForConfigurationCache
    def "can publish different resolved versions for the same module"() {
        given:
        javaLibrary(ivyRepo.module("org", "foo", "1.0")).publish()
        javaLibrary(ivyRepo.module("org", "foo", "1.1")).publish()
        createBuildScripts """
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            usage('java-api') {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }

            dependencies {
                api 'org:foo:1.0'
                compileOnly 'org:foo:1.1'
            }
        """

        when:
        succeeds "publish"

        then:
        outputDoesNotContain(DefaultIvyPublication.UNSUPPORTED_FEATURE)
        javaLibrary.assertPublishedAsJavaModule()
        javaLibrary.assertApiDependencies("org:foo:1.1")
        javaLibrary.assertRuntimeDependencies("org:foo:1.0")
    }

    // This is a weird test case, because why would you have a substitution rule
    // for a first level dependency? However it may be that you implicitly get a
    // substitution rule (via a plugin for example) that you are not aware of.
    // Ideally we should warn when such things happen (linting).
    @Issue("https://github.com/gradle/gradle/issues/14039")
    @ToBeFixedForConfigurationCache
    def "substituted project dependencies are also substituted in the generated Ivy file"() {
        createBuildScripts("""
            dependencies {
                implementation project(":a")
            }

            configurations.all {
                resolutionStrategy.dependencySubstitution {
                    substitute(project(':a')).using(project(':b'))
                }
            }

            publishing {
                publications {
                    maven(IvyPublication) {
                        from components.java
                        versionMapping {
                            ${apiUsingUsage()}
                            ${runtimeUsingUsage()}
                        }
                    }

                }
            }
        """)
        settingsFile << """
            include 'a'
            include 'b'
        """
        file("a/build.gradle") << """
            plugins {
                id 'java-library'
            }
            group = 'com.first'
            version = '1.1'
        """
        file("b/build.gradle") << """
            plugins {
                id 'java-library'
            }
            group = 'com.second'
            version = '1.2'
        """

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedIvy.assertConfigurationDependsOn('runtime', 'com.second:b:1.2')
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("com.second", "b", "1.2")
            noMoreDependencies()
        }
    }
}

class AbstractIvyPublishResolvedVersionsJavaIntegTest extends AbstractIvyPublishIntegTest  {
    IvyJavaModule javaLibrary = javaLibrary(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))

    static String allVariants() {
        " allVariants { fromResolutionResult() } "
    }

    static String noop() { "" }

    static String apiUsingUsage(String config = "fromResolutionResult()") {
        """ usage("java-api") { $config } """
    }

    static String runtimeUsingUsage(String config = "fromResolutionResult()") {
        """ usage("java-runtime") { $config } """
    }

    void createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            repositories {
                // use for resolving
                ivy { url "${ivyRepo.uri}" }
            }

            publishing {
                repositories {
                    // used for publishing
                    ivy { url "${ivyRepo.uri}" }
                }
            }

            group = 'org.gradle.test'
            version = '1.9'

$append
"""

    }
}
