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

class StrictDependenciesResolveIntegrationTest extends AbstractStrictDependenciesIntegrationTest {
    def "should not downgrade dependency version when an external transitive dependency has strict version"() {
        given:
        def foo15 = mavenHttpRepo.module("org", "foo", '15').withModuleMetadata().publish()
        def foo17 = mavenHttpRepo.module("org", "foo", '17').withModuleMetadata().publish()
        def bar10 = mavenHttpRepo.module("org", "bar", "1.0")
            .dependsOn(foo15, rejects: [']15,)'])
            .withModuleMetadata()
            .publish()

        buildFile << """
            repositories {
                maven { 
                   url "${mavenHttpRepo.uri}" 
                   useGradleMetadata()
                }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'org:foo:17'
                conf 'org:bar:1.0'
            }                       
        """

        when:
        foo17.pom.expectGet()
        foo17.moduleMetadata.expectGet()
        bar10.pom.expectGet()
        bar10.moduleMetadata.expectGet()
        fails 'checkDeps'

        then:
        failure.assertHasCause('Cannot find a version of \'org:foo\' that satisfies the constraints: prefers 17, prefers 15, rejects ]15,)')

    }

    void "should pass if strict version ranges overlap using external dependencies"() {
        given:
        def foo10 = mavenHttpRepo.module("org", "foo", '1.0').withModuleMetadata().publish()
        def foo11 = mavenHttpRepo.module("org", "foo", '1.1').withModuleMetadata().publish()
        def foo12 = mavenHttpRepo.module("org", "foo", '1.2').withModuleMetadata().publish()
        def foo13 = mavenHttpRepo.module("org", "foo", '1.3').withModuleMetadata().publish()
        def bar10 = mavenHttpRepo.module('org', 'bar', '1.0')
            .dependsOn(mavenHttpRepo.module('org', 'foo', '[1.1,1.3]'), rejects: [']1.3,)'])
            .withModuleMetadata()
            .publish()

        buildFile << """
            repositories {
                maven { 
                   url "${mavenHttpRepo.uri}"
                   useGradleMetadata()
                }
            }

            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    version { strictly '[1.0,1.2]' }
                }
                conf 'org:bar:1.0'
            }
            
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'foo-1.2.jar']
                }
            }                  
        """

        when:
        foo10.rootMetaData.expectGet()
        foo12.pom.expectGet()
        foo12.moduleMetadata.expectGet()
        bar10.pom.expectGet()
        bar10.moduleMetadata.expectGet()
        foo13.pom.expectGet()
        foo13.moduleMetadata.expectGet()
        foo12.artifact.expectGet()
        bar10.artifact.expectGet()
        run ':checkDeps'

        then:
        noExceptionThrown()
    }


    def "should fail if 2 strict versions disagree (external)"() {
        given:
        def foo15 = mavenHttpRepo.module("org", "foo", '15').withModuleMetadata().publish()
        def foo17 = mavenHttpRepo.module("org", "foo", '17').withModuleMetadata().publish()
        def bar10 = mavenHttpRepo.module("org", "bar", "1.0")
            .dependsOn(foo15, rejects: [']15,)'])
            .withModuleMetadata()
            .publish()

        buildFile << """
            repositories {
                maven { 
                   url "${mavenHttpRepo.uri}"
                   useGradleMetadata()
                }
            }

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
        foo17.pom.expectGet()
        foo17.moduleMetadata.expectGet()
        bar10.pom.expectGet()
        bar10.moduleMetadata.expectGet()
        fails 'checkDeps'

        then:
        failure.assertHasCause('Cannot find a version of \'org:foo\' that satisfies the constraints: prefers 17, rejects ]17,), prefers 15, rejects ]15,)')

    }
}
