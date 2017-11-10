/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.RequiresFeatureEnabled

@RequiresFeatureEnabled
class StrictDependenciesResolveIntegrationTest extends AbstractStrictDependenciesIntegrationTest {
    def "should not downgrade dependency version when an external transitive dependency has strict version"() {
        given:
        def foo15 = module("org", "foo", '15').publish()
        def foo17 = module("org", "foo", '17').publish()
        def bar10 = module("org", "bar", "1.0")
            .dependsOn(foo15, rejects: [']15,)'])
            .publish()

        buildFile << """
            $repository

            configurations {
                conf
            }
            dependencies {
                conf 'org:foo:17'
                conf 'org:bar:1.0'
            }                       
        """

        when:
        foo17.assertGetMetadata()
        bar10.assertGetMetadata()
        fails 'checkDeps'

        then:
        failure.assertHasCause('Cannot find a version of \'org:foo\' that satisfies the constraints: prefers 17, prefers 15, rejects ]15,)')

    }

    void "should pass if strict version ranges overlap using external dependencies"() {
        given:
        def foo10 = module("org", "foo", '1.0').publish()
        def foo11 = module("org", "foo", '1.1').publish()
        def foo12 = module("org", "foo", '1.2').publish()
        def foo13 = module("org", "foo", '1.3').publish()
        def bar10 = module('org', 'bar', '1.0')
            .dependsOn(module('org', 'foo', '[1.1,1.3]'), rejects: [']1.3,)'])
            .publish()

        buildFile << """
            $repository

            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    version { strictly '[1.0,1.2]' }
                }
                conf 'org:bar:1.0'
            }
                         
        """

        when:
        foo10.rootMetaData.expectGet()
        foo12.assertGetMetadata()
        bar10.assertGetMetadata()
        foo13.assertGetMetadata()
        foo12.assertGetArtifact()
        bar10.assertGetArtifact()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:[1.0,1.2]", "org:foo:1.2")
                edge('org:bar:1.0', 'org:bar:1.0') {
                    edge("org:foo:[1.1,1.3]", "org:foo:1.2")
                }
            }
        }
    }


    def "should fail if 2 strict versions disagree (external)"() {
        given:
        def foo15 = module("org", "foo", '15').publish()
        def foo17 = module("org", "foo", '17').publish()
        def bar10 = module("org", "bar", "1.0")
            .dependsOn(foo15, rejects: [']15,)'])
            .publish()

        buildFile << """
            $repository

            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    version {
                       strictly '17'
                    }
                }
                conf 'org:bar:1.0'
            }                       
        """

        when:
        foo17.assertGetMetadata()
        bar10.assertGetMetadata()
        fails 'checkDeps'

        then:
        failure.assertHasCause('Cannot find a version of \'org:foo\' that satisfies the constraints: prefers 17, rejects ]17,), prefers 15, rejects ]15,)')

    }
}
