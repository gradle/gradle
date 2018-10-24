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

    def "doesn't search for module in repository when rule says so"() {
        def mod = ivyHttpRepo.module('org', 'foo', '1.0').publish()

        given:
        repositories {
            maven("contentFilter { details -> details.notFound() }")
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
    }

    def "doesn't try to list module versions in repository when rule says so"() {
        def mod = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def ivyDirectoryList = ivyHttpRepo.directoryList('org', 'foo')

        given:
        repositories {
            maven("contentFilter { details -> details.notFound() }")
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
    }

    def "can filter based on the module identifier"() {
        def mod1 = ivyHttpRepo.module('org', 'foo', '1.0').publish()
        def mod2Ivy = ivyHttpRepo.module('org', 'bar', '1.0').publish()
        def mod2Maven = mavenHttpRepo.module('org', 'bar', '1.0')

        given:
        repositories {
            maven("""contentFilter { details ->
                if (details.id.name == 'foo') { 
                   details.notFound() 
                }
            }""")
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
        mod2Maven.artifact.expectHeadMissing()

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
    }

    void repositories(@DelegatesTo(value=RepositorySpec, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
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
