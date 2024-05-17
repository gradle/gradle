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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.file.TestFile

class RepositoryContentFilteringIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            configurations {
                conf
            }
        """
        resolve = new ResolveTestFixture(buildFile, 'conf')
        resolve.prepare()
    }

    def "can exclude a module from a repository using #notation"() {
        def mod = ivyHttpRepo.module('org', 'foo', '1.0').publish()

        given:
        repositories {
            maven("content { $notation }")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.0"
            }
        """

        when:
        mod.ivy.expectGet()
        mod.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
            }
        }

        where:
        notation << [
                "excludeGroup('org')",
                "excludeGroupByRegex('or.+')"
        ]
    }

    def "can include a module from a repository using #notation"() {
        def mod = ivyHttpRepo.module('org', 'foo', '1.0').publish()

        given:
        repositories {
            maven("content { $notation }")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.0"
            }
        """

        when:
        mod.ivy.expectGet()
        mod.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
            }
        }

        where:
        notation << [
                "includeGroup('other')",
                "includeGroupByRegex('oth[a-z]+')"
        ]
    }

    def "doesn't try to list module versions in repository when rule excludes group using #notation"() {
        def mod = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def ivyDirectoryList = ivyHttpRepo.directoryList('org', 'foo')

        given:
        repositories {
            maven("content { $notation }")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:+"
            }
        """

        when:
        ivyDirectoryList.allowGet()
        mod.ivy.expectGet()
        mod.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:+', 'org:foo:1.0')
            }
        }

        where:
        notation << [
                "excludeGroup('org')",
                "excludeGroupByRegex('or.+')"
        ]
    }

    def "doesn't try to list module versions in repository when rule includes group using #notation"() {
        def mod = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def ivyDirectoryList = ivyHttpRepo.directoryList('org', 'foo')

        given:
        repositories {
            maven("content { $notation }")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:+"
            }
        """

        when:
        ivyDirectoryList.allowGet()
        mod.ivy.expectGet()
        mod.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:+', 'org:foo:1.0')
            }
        }

        where:
        notation << [
                "includeGroup('other')",
                "includeGroupByRegex('oth[a-z]+')"
        ]
    }

    def "can exclude a specific module using #notation"() {
        def mod1 = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def mod2Ivy = ivyHttpRepo.module('org', 'bar', '1.0').publish()
        def mod2Maven = mavenHttpRepo.module('org', 'bar', '1.0')

        given:
        repositories {
            maven("""content { $notation }""")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.0"
                conf "org:bar:1.0"
            }
        """

        when:
        mod1.ivy.expectGet()
        mod1.artifact.expectGet()

        mod2Maven.pom.expectGetMissing()

        mod2Ivy.ivy.expectGet()
        mod2Ivy.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
                module('org:bar:1.0')
            }
        }

        where:
        notation << [
                "excludeModule('org', 'foo')",
                "excludeModuleByRegex('or.+', 'f[o]{1,2}')"
        ]
    }

    def "can include a specific module using #notation"() {
        def mod1 = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def mod2Ivy = ivyHttpRepo.module('org', 'bar', '1.0').publish()
        def mod2Maven = mavenHttpRepo.module('org', 'bar', '1.0')

        given:
        repositories {
            maven("""content { $notation }""")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.0"
                conf "org:bar:1.0"
            }
        """

        when:
        mod1.ivy.expectGet()
        mod1.artifact.expectGet()

        mod2Maven.pom.expectGetMissing()

        mod2Ivy.ivy.expectGet()
        mod2Ivy.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
                module('org:bar:1.0')
            }
        }

        where:
        notation << [
                "includeModule('org', 'bar')",
                "includeModuleByRegex('or.+', 'b[ar]+')",
        ]
    }

    /**
     * Use case: allow different configurations to resolve the same dependencies but not necessarily from
     * the same repositories. For example, for a distribution we would only allow fetching from blessed
     * repositories while for tests, we would be more lenient. This can be achieved by checking the name
     * of the configuration being resolved, in the rule.
     */
    def "can filter by configuration name (#notation)"() {
        def mod = ivyHttpRepo.module('org', 'foo', '1.0').publish()

        given:
        repositories {
            maven("""content {
                $notation
            }""")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.0"
            }
        """

        when:
        mod.ivy.expectGet()
        mod.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
            }
        }

        where:
        notation << [
                'onlyForConfigurations("other")',
                'notForConfigurations("conf")'
        ]
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "two configurations can use the same repositories with filtering and do not interfere with each other"() {
        def mod = mavenHttpRepo.module('org', 'foo', '1.0').publish()

        given:
        repositories {
            maven("""content {
                onlyForConfigurations("conf2")
            }""")
        }
        buildFile << """
            configurations {
                conf2
            }
            dependencies {
                conf "org:foo:1.0"
                conf2 "org:foo:1.0"
            }
            tasks.register("verify") {
                doFirst {
                    $check1
                    $check2
                }
            }
        """

        when:
        mod.pom.expectGet()
        mod.artifact.expectGet()

        then:
        succeeds 'verify'

        where:
        check1 << [checkConfIsUnresolved(), checkConf2IsResolved()]
        check2 << [checkConf2IsResolved(), checkConfIsUnresolved()]
    }

    /**
     * Use case: explain that a repository doesn't contain dependencies with specific attributes.
     * This can be useful when a repository only contains dependencies of a certain type (for example, native binaries or JS libraries)
     * so it wouldn't be necessary to look for POM files in them for example.
     */
    def "can filter by attributes"() {
        def mod = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        buildFile << """
            def colorAttribute = Attribute.of('colorAttribute', String)
        """
        given:
        repositories {
            maven("""content {
                onlyForAttribute(colorAttribute, 'red')
            }""")
            ivy()
        }
        buildFile << """
            configurations {
                conf {
                    attributes {
                        attribute(colorAttribute, 'blue')
                    }
                }
            }
            dependencies {
                conf("org:foo:1.0")
            }
        """

        when:
        mod.ivy.expectGet()
        mod.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
            }
        }
    }

    def "can exclude by module version using #notation"() {
        def modIvy = ivyHttpRepo.module('org', 'foo', '1.1').publish()
        def modMaven = mavenHttpRepo.module('org', 'foo', '1.0').publish()

        given:
        repositories {
            maven("""content { details ->
                $notation
            }""")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.1"
            }
        """

        when:
        modIvy.ivy.expectGet()
        modIvy.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.1')
            }
        }

        where:
        notation << [
                "excludeVersion('org', 'foo', '1.1')",
                "excludeVersion('org', 'foo', '[1.0,)')",
                "excludeVersion('org', 'foo', '[1.0,1.2)')",
                "excludeVersion('org', 'foo', '(,1.1]')",
                "excludeVersion('org', 'foo', '(,1.2]')",
                "excludeVersionByRegex('or.+', 'f.+', '1\\\\.[1-2]')"
        ]
    }

    def "can include by module version using #notation"() {
        def modIvy = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def modMaven = mavenHttpRepo.module('org', 'foo', '1.1').publish()

        given:
        repositories {
            maven("""content {
                $notation
            }""")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.1"
            }
        """

        when:
        modMaven.pom.expectGet()
        modMaven.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.1')
            }
        }

        where:
        notation << [
                "includeVersion('org', 'foo', '1.1')",
                "includeVersion('org', 'foo', '[1.1,)')",
                "includeVersion('org', 'foo', '[1.1,1.3)')",
                "includeVersion('org', 'foo', '(,1.1]')",
                "includeVersion('org', 'foo', '(,1.2]')",
                "includeVersionByRegex('or.+', 'f.+', '1\\\\.[1-3]')"
        ]
    }

    def "can declare that a repository doesn't contain snapshots"() {
        // doesn't really make sense to look for "SNAPSHOT" in an Ivy repository, but this is for the test
        def modIvy = ivyHttpRepo.module('org', 'foo', '1.0-SNAPSHOT').publish()

        given:
        repositories {
            maven("""mavenContent { releasesOnly() }""")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.0-SNAPSHOT"
            }
        """

        when:
        modIvy.ivy.expectGet()
        modIvy.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0-SNAPSHOT')
            }
        }
    }

    def "can declare that a repository only contains snapshots (unique = #unique)"() {
        def snapshotModule = mavenHttpRepo.module('org', 'foo', '1.0-SNAPSHOT')
        if (!unique) {
            snapshotModule.withNonUniqueSnapshots()
        }
        snapshotModule.publish()
        def release = ivyHttpRepo.module('org', 'bar', '1.0').publish()

        given:
        repositories {
            maven("""mavenContent { snapshotsOnly() }""")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.0-SNAPSHOT"
                conf "org:bar:1.0"
            }
        """

        when:
        // looks for the Maven pom file because it's a snapshot
        snapshotModule.metaData.expectGet() // gets the maven-metadata.xml file to get the latest snapshot version
        snapshotModule.pom.expectGet()
        snapshotModule.artifact.expectGet()

        // but doesn't look for the release because it's not a snapshot
        release.ivy.expectGet()
        release.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                if (unique) {
                    snapshot('org:foo:1.0-SNAPSHOT', snapshotModule.uniqueSnapshotVersion)
                } else {
                    module('org:foo:1.0-SNAPSHOT')
                }
                module('org:bar:1.0')
            }
        }

        where:
        unique << [true, false]
    }

    def "releases only and dynamic selector"() {
        // doesn't really make sense to look for "SNAPSHOT" in an Ivy repository, but this is for the test
        def modIvy = ivyHttpRepo.module('org', 'foo', '1.0-SNAPSHOT').publish()

        // we explicitly want to ignore the Maven module
        def modMaven = mavenHttpRepo.module('org', 'foo', '1.0-SNAPSHOT').publish()
        def mavenVersionList = mavenHttpRepo.module('org', 'foo').rootMetaData
        def mavenVersionList2 = mavenHttpRepo.directoryList('org', 'foo')
        def ivyVersionList = ivyHttpRepo.directoryList('org', 'foo')

        given:
        repositories {
            maven("""mavenContent { releasesOnly() }""")
            ivy()
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.+"
            }
        """

        when:
        mavenVersionList.expectGet()
        ivyVersionList.expectGet()
        modIvy.ivy.expectGet()
        modIvy.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:1.+', 'org:foo:1.0-SNAPSHOT')
            }
        }
    }

    def "presence of snapshots in a repo shouldn't prevent from getting latest release"() {
        def latestSnapshot = mavenHttpRepo.module('org', 'foo', '1.1-SNAPSHOT').publish()
        def latestRelease = mavenHttpRepo.module('org', 'foo', '1.0').publish()

        given:
        repositories {
            maven("""mavenContent { releasesOnly() }""")
        }
        buildFile << """
            dependencies {
                conf "org:foo:latest.release"
            }
        """
        when:
        latestSnapshot.rootMetaData.expectGet()
        latestRelease.pom.expectGet()
        latestRelease.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:latest.release', 'org:foo:1.0')
            }
        }
    }

    def "presence of releases in a repo shouldn't prevent from getting latest snapshot"() {
        def latestRelease = mavenHttpRepo.module('org', 'foo', '1.1').publish()
        def latestSnapshot = mavenHttpRepo.module('org', 'foo', '1.0-SNAPSHOT').publish()

        given:
        repositories {
            maven("""mavenContent { snapshotsOnly() }""")
        }
        buildFile << """
            dependencies {
                conf "org:foo:latest.integration"
            }
        """
        when:
        latestRelease.rootMetaData.expectGet()
        latestSnapshot.metaData.expectGet()
        latestSnapshot.pom.expectGet()
        latestSnapshot.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                snapshot('org:foo:1.0-SNAPSHOT', latestSnapshot.uniqueSnapshotVersion, 'latest.integration')
            }
        }
    }

    def "mavenContent does not resolve repository url eagerly"() {
        given:
        buildFile << """
            repositories {
                maven {
                    url = { throw new RuntimeException("url resolved") }
                    mavenContent { snapshotsOnly() }
                }
            }
            dependencies {
                conf "org:foo:latest.integration"
            }
        """

        expect:
        succeeds("help")
    }

    def "can filter dynamic versions using #notation"() {
        def modIvy = ivyHttpRepo.module('org', 'foo', '1.1').publish()
        def modMaven = mavenHttpRepo.module('org', 'foo', '1.0').publish()

        given:
        repositories {
            maven("""content { details ->
                $notation
            }""")
            ivy("""content { details ->
                $notation
            }""")
        }
        buildFile << """
            dependencies {
                conf "org:foo:1.+"
            }
        """

        when:
        ivyHttpRepo.directoryList('org', 'foo').expectGet()
        mavenHttpRepo.getModuleMetaData('org', 'foo').expectGet()
        modMaven.pom.expectGet()
        modMaven.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:1.+', 'org:foo:1.0')
            }
        }

        where:
        notation << [
            "excludeVersion('org', 'foo', '1.1')",
            "excludeVersionByRegex('org', 'foo', '1\\\\.1')",
            "includeVersion('org', 'foo', '1.0')",
            "includeVersionByRegex('org', 'foo', '1\\\\.0')",
        ]
    }

    static String checkConfIsUnresolved() {
        """def confIncoming = configurations.conf.incoming.resolutionResult.allDependencies
                    assert confIncoming.every { it instanceof UnresolvedDependencyResult }"""
    }

    static String checkConf2IsResolved() {
        """def conf2Incoming = configurations.conf2.incoming.resolutionResult.allDependencies
                    assert conf2Incoming.every { it instanceof ResolvedDependencyResult }
                    assert configurations.conf2.files.name == ['foo-1.0.jar']"""
    }

    void repositories(@DelegatesTo(value = RepositorySpec, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
        def delegate = new RepositorySpec()
        spec.delegate = delegate
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        delegate.complete(buildFile)
    }

    class RepositorySpec {
        private final StringBuilder dsl = new StringBuilder()

        RepositorySpec() {
            dsl << "repositories {"
        }

        void maven(String conf = "") {
            dsl << """
                maven {
                    url "${mavenHttpRepo.uri}"
                    $conf
                }
            """
        }

        void ivy(String conf = "") {
            dsl << """
                ivy {
                    url "${ivyHttpRepo.uri}"
                    $conf
                }
            """
        }

        void complete(TestFile to) {
            dsl << "\n}"
            to << dsl
            dsl.setLength(0)
        }
    }
}
