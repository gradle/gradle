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
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer

class ExclusiveRepositoryContentFilteringIntegrationTest extends AbstractHttpDependencyResolutionTest {
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

    def "can include a module from a repository using #notation (Maven 1st)"() {
        def foo = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def bar = mavenHttpRepo.module('other', 'bar', '2.0').publish()
        given:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
                exclusiveContent {
                   forRepository {
                      ivy { url "${ivyHttpRepo.uri}" }
                   }
                   filter {
                      $notation
                   }
                }
            }
            dependencies {
                conf "org:foo:1.0"
                conf "other:bar:2.0"
            }
        """

        when:
        foo.ivy.expectGet()
        foo.artifact.expectGet()
        bar.pom.expectGet()
        bar.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
                module('other:bar:2.0')
            }
        }

        where:
        notation << [
            "includeGroup 'org'",
            "includeGroupByRegex 'org.*'",
            "includeModule('org', 'foo')",
            "includeModuleByRegex('or[g]', 'f[o]+')",
            "includeVersion('org', 'foo', '1.0')",
            "includeVersion('org', 'foo', '[1.0,)')",
            "includeVersion('org', 'foo', '[1.0,1.2]')",
            "includeVersion('org', 'foo', '[1.0,1.2)')",
            "includeVersion('org', 'foo', '(,1.1]')",
            "includeVersionByRegex('or[g]', 'f[o]+', '1[.].+')",
        ]
    }

    def "can include a module from a repository using #notation and combine with local repository filter"() {
        def foo = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def barIvy = ivyHttpRepo.module('other', 'bar', '2.0')
        def bar = mavenHttpRepo.module('other', 'bar', '2.0').publish()
        given:
        buildFile << """
            repositories {
                exclusiveContent {
                   forRepository {
                      ivy {
                         url "${ivyHttpRepo.uri}"
                         content {
                            includeGroup('other') // says that we can find "bar", not exclusively
                         }
                      }
                   }
                   filter {
                      $notation
                   }
                }
                maven { url "${mavenHttpRepo.uri}" }
            }
            dependencies {
                conf "org:foo:1.0"
                conf "other:bar:2.0"
            }
        """

        when:
        foo.ivy.expectGet()
        foo.artifact.expectGet()
        barIvy.ivy.expectGetMissing()
        bar.pom.expectGet()
        bar.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
                module('other:bar:2.0')
            }
        }

        where:
        notation << [
            "includeGroup 'org'",
            "includeGroupByRegex 'org.*'",
            "includeModule('org', 'foo')",
            "includeModuleByRegex('or[g]', 'f[o]+')",
            "includeVersion('org', 'foo', '1.0')",
            "includeVersion('org', 'foo', '[1.0,)')",
            "includeVersion('org', 'foo', '[1.0,1.2]')",
            "includeVersion('org', 'foo', '[1.0,1.2)')",
            "includeVersion('org', 'foo', '(,1.1]')",
            "includeVersionByRegex('or[g]', 'f[o]+', '1[.].+')",
        ]
    }

    def "can declare a group of repositories to search for artifacts exclusively using #notation"() {
        def foo = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def bar = mavenHttpRepo.module('other', 'bar', '2.0').publish()
        def otherMavenFileRepo = new MavenFileRepository(file("maven-repo2"))
        def otherServer = new RepositoryHttpServer(temporaryFolder)
        def otherMavenRepo = new MavenHttpRepository(otherServer, "/mavenrepo2", HttpRepository.MetadataType.DEFAULT, otherMavenFileRepo)
        otherServer.start()
        def missingFoo = otherMavenRepo.module('org', 'foo', '1.0')

        given:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
                exclusiveContent {
                   forRepository {
                      maven { url "${otherMavenRepo.uri}" }
                   }
                   forRepository {
                      ivy { url "${ivyHttpRepo.uri}" }
                   }
                   filter {
                      $notation
                   }
                }
            }
            dependencies {
                conf "org:foo:1.0"
                conf "other:bar:2.0"
            }
        """

        when:
        missingFoo.pom.expectGetMissing()
        foo.ivy.expectGet()
        foo.artifact.expectGet()
        bar.pom.expectGet()
        bar.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
                module('other:bar:2.0')
            }
        }

        cleanup:
        otherServer.stop()

        where:
        notation << [
            "includeGroup 'org'",
            "includeGroupByRegex 'org.*'",
            "includeModule('org', 'foo')",
            "includeModuleByRegex('or[g]', 'f[o]+')",
            "includeVersion('org', 'foo', '1.0')",
            "includeVersion('org', 'foo', '[1.0,)')",
            "includeVersion('org', 'foo', '[1.0,1.2]')",
            "includeVersion('org', 'foo', '[1.0,1.2)')",
            "includeVersion('org', 'foo', '(,1.1]')",
            "includeVersionByRegex('or[g]', 'f[o]+', '1[.].+')",
        ]
    }

    def "can reuse an existing repo"() {
        def foo = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def bar = mavenHttpRepo.module('other', 'bar', '2.0').publish()
        given:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
                def repo = ivy { url "${ivyHttpRepo.uri}" }
                exclusiveContent {
                   forRepositories(repo)
                   filter {
                      includeGroup("org")
                   }
                }
            }
            dependencies {
                conf "org:foo:1.0"
                conf "other:bar:2.0"
            }
        """

        when:
        foo.ivy.expectGet()
        foo.artifact.expectGet()
        bar.pom.expectGet()
        bar.artifact.expectGet()

        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0')
                module('other:bar:2.0')
            }
        }
    }
}
