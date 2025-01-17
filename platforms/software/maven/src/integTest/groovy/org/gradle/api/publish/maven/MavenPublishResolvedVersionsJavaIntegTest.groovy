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

package org.gradle.api.publish.maven

import org.gradle.api.attributes.Category
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenJavaModule
import spock.lang.Issue

class MavenPublishResolvedVersionsJavaIntegTest extends MavenPublishResolvedVersionsJavaFixture {
    /**
     * This use case corresponds to the cases where the published versions should be different
     * from the versions published using the default configurations (compileClasspath, runtimeClasspath).
     * This can be the case if there are multiple compile classpath and one should be preferred for publication,
     * or when the component is not a Java library and we don't have a default.
     */
    def "can publish resolved versions from a different configuration (#config)"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

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
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
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
        javaLibrary.parsedPom.scopes.compile.assertDependsOn('org.test:foo:1.0')
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn('org.test:bar:1.1')

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }

        where:
        config << [
            "fromResolutionOf('extra')",
            "fromResolutionOf(project.configurations.extra)"
        ]
    }

    def "can publish resolved versions from dependency constraints (#apiMapping, #runtimeMapping)"() {
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

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
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
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
        def dependencies = javaLibrary.parsedPom.dependencyManagement.dependencies[0].dependency
        dependencies.size() == 1
        dependencies[0].with {
            assert it.groupId.text() == 'org.test'
            assert it.artifactId.text() == 'bar'
            assert it.version.text() == '1.1'
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
        javaLibrary.parsedPom.scopes.compile.assertDependsOn('org.test:foo:1.0')
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn('org.test:bar:1.1')

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }

        where:
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')")]
        ].combinations() + [[allVariants(), noop()]])
    }

    def "dependency constraints which are unresolved are published as is"() {
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        given:
        createBuildScripts("""
            dependencies {
                constraints {
                    api "org.test:bar:[1.0, 2.0["
                }
                api "org.test:foo:1.0"
            }
            publishing {
                publications {
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            constraint("org.test:bar:[1.0, 2.0[") {
                exists()
            }
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        def dependencies = javaLibrary.parsedPom.dependencyManagement.dependencies[0].dependency
        dependencies.size() == 1
        dependencies[0].with {
            assert it.groupId.text() == 'org.test'
            assert it.artifactId.text() == 'bar'
            assert it.version.text() == '[1.0, 2.0)'
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            constraint("org.test:bar:[1.0, 2.0[") {
                exists()
            }
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
        }

    }

    // This test documents the existing behavior, not necessarily the best one
    def "import scope makes use of runtime classpath"() {
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata()
            .withVariant('api') { attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM) }
            .withVariant('runtime') { attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM) }.publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata()
            .withVariant('api') { attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM) }
            .withVariant('runtime') { attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM) }.publish()

        given:
        createBuildScripts("""
            dependencies {
                constraints {
                    api "org.test:bar:1.0"
                }
                api "org.test:foo:1.0"
                runtimeOnly(platform("org.test:bar:1.1"))
            }
            publishing {
                publications {
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
        javaLibrary.assertPublished()
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            constraint("org.test:bar:1.0") {
                exists()
            }
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
        def dependencies = javaLibrary.parsedPom.dependencyManagement.dependencies[0].dependency
        dependencies.size() == 2
        dependencies[0].with {
            assert it.groupId.text() == 'org.test'
            assert it.artifactId.text() == 'bar'
            assert it.version.text() == '1.0'
        }
        dependencies[1].with {
            assert it.groupId.text() == 'org.test'
            assert it.artifactId.text() == 'bar'
            assert it.version.text() == '1.1'
            assert it.scope.text() == 'import'
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            constraint("org.test:bar:1.1") {
                exists()
            }
            dependency("org.test:bar:1.1") {
                exists()
            }
            dependency("org.test:foo:1.0") {
                exists()
            }
            noMoreDependencies()
        }
    }

    // This is a weird test case, because why would you have a substitution rule
    // for a first level dependency? However it may be that you implicitly get a
    // substitution rule (via a plugin for example) that you are not aware of.
    // Ideally we should warn when such things happen (linting).
    def "substituted dependencies are also substituted in the generated POM file"() {
        javaLibrary(mavenRepo.module("org", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org", "bar", "1.0"))
            .dependsOn("org", "baz", "1.0")
            .withModuleMetadata()
            .publish()
        javaLibrary(mavenRepo.module("org", "baz", "1.0")).withModuleMetadata().publish()

        given:
        createBuildScripts("""
            dependencies {
                implementation 'org:foo:1.0'
                implementation 'org:bar:1.0'
            }

            $substitution

            publishing {
                publications {
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
        javaLibrary.assertPublished()
        javaLibrary.parsedPom.scope("runtime") {
            assert dependencies.size() == 2
            def deps = dependencies.values()
            assert deps[0].artifactId == 'baz' // because of substitution
            assert deps[1].artifactId == 'bar'
        }
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

    // This is a weird test case, because why would you have a substitution rule
    // for a first level dependency? However it may be that you implicitly get a
    // substitution rule (via a plugin for example) that you are not aware of.
    // Ideally we should warn when such things happen (linting).
    @Issue("https://github.com/gradle/gradle/issues/14039")
    def "substituted project dependencies are also substituted in the generated POM file"() {
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
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
        javaLibrary.assertPublished()
        javaLibrary.parsedPom.scope("runtime") {
            assert dependencies.size() == 1
            def deps = dependencies.values()
            assert deps[0].artifactId == 'b' // because of substitution
            assert deps[0].groupId == 'com.second'
            assert deps[0].version == '1.2'
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("com.second", "b", "1.2")
            noMoreDependencies()
        }
    }

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
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
        javaLibrary.assertPublished()
        javaLibrary.parsedPom.scope("runtime") {
            assert dependencies.size() == 1
            def dep = dependencies.values()[0]
            assert dep.groupId == 'com.acme'
            assert dep.artifactId == 'lib'
            assert dep.version == '1.45'
        }
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("com.acme", "lib", "1.45")
            noMoreDependencies()
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/28225")
    def "maps version of dependency with artifact"() {
        given:
        mavenRepo.module("org", "foo", "2.1").artifact(classifier: "cls").publish()

        settingsFile << "rootProject.name = 'producer'"
        buildFile << """
            plugins {
                id("java-library")
                id("maven-publish")
            }

            ${mavenTestRepository()}

            group = "org.example"
            version = "1.0"

            dependencies {
                implementation "org:foo:2.+:cls"
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                        versionMapping {
                            usage('java-api') {
                                fromResolutionOf('runtimeClasspath')
                            }
                            usage('java-runtime') {
                                fromResolutionResult()
                            }
                        }
                    }
                }
                ${mavenTestRepository()}
            }
        """

        when:
        succeeds("publish")

        then:
        def pom = mavenRepo.module("org.example", "producer", "1.0").parsedPom

        def dependencies = pom.scopes.runtime.dependencies.values()
        dependencies.size() == 1

        def dependency = dependencies[0]
        dependency.groupId == "org"
        dependency.artifactId == "foo"
        dependency.version == "2.1"
        dependency.classifier == "cls"
    }

}

class MavenPublishJavaLibraryRuntimeLastResolvedVersionsJavaIntegTest extends MavenPublishResolvedVersionsJavaFixture {
    def "can publish java-library with dependencies (#apiMapping, #runtimeMapping)"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "foo", "1.1")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:foo:[1.0,1.0]"
                runtimeOnly "org.test:bar:+"
                runtimeOnly "org.test:foo:+"
            }
            publishing {
                publications {
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
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
        javaLibrary.parsedPom.scopes.compile.assertDependsOn('org.test:foo:1.0')
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn('org.test:bar:1.1')

        and:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                // With Maven, can't have different versions for a dependency between compile and runtime
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        where:
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')"), apiUsingUsage("fromResolutionOf(project.configurations.compileClasspath)")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)")],
        ].combinations() + [[allVariants(), noop()]])
    }
}

class MavenPublishJavaLibraryRuntimeFirstResolvedVersionsJavaIntegTest extends MavenPublishResolvedVersionsJavaFixture {
    def "can publish java-library with dependencies (#runtimeMapping, #apiMapping)"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "foo", "1.1")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:foo:[1.0,1.0]"
                runtimeOnly "org.test:bar:+"
                runtimeOnly "org.test:foo:+"
            }
            publishing {
                publications {
                    maven(MavenPublication) {
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
        javaLibrary.mavenModule.removeGradleMetadataRedirection()
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
        javaLibrary.parsedPom.scopes.compile.assertDependsOn('org.test:foo:1.0')
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn('org.test:bar:1.1')

        and:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                // With Maven, can't have different versions for a dependency between compile and runtime
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.1.jar", "foo-1.1.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                // With Maven, can't have different versions for a dependency between compile and runtime
                expectFiles "bar-1.1.jar", "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        where:
        [apiMapping, runtimeMapping] << ([
            [apiUsingUsage(), apiUsingUsage("fromResolutionOf('compileClasspath')"), apiUsingUsage("fromResolutionOf(project.configurations.compileClasspath)")],
            [runtimeUsingUsage(), runtimeUsingUsage("fromResolutionOf('runtimeClasspath')"), runtimeUsingUsage("fromResolutionOf(project.configurations.runtimeClasspath)")],
        ].combinations() + [[allVariants(), noop()]])
    }
}

abstract class MavenPublishResolvedVersionsJavaFixture extends AbstractMavenPublishIntegTest {
    MavenJavaModule javaLibrary = javaLibrary(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))

    protected static String allVariants() {
        " allVariants { fromResolutionResult() } "
    }

    protected static String noop() { "" }

    protected static String apiUsingUsage(String config = "fromResolutionResult()") {
        """ usage("java-api") { $config } """
    }

    protected static String runtimeUsingUsage(String config = "fromResolutionResult()") {
        """ usage("java-runtime") { $config } """
    }

    protected void createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-library'

            repositories {
                // use for resolving
                maven { url = "${mavenRepo.uri}" }
            }

            publishing {
                repositories {
                    // used for publishing
                    maven { url = "${mavenRepo.uri}" }
                }
            }

            group = 'org.gradle.test'
            version = '1.9'

$append
"""
    }
}
