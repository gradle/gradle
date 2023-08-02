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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class IvyDynamicRevisionMultiRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def setup() {
        settingsFile << "rootProject.name = 'test' "
    }

    @Issue("GRADLE-2502")
    def "can resolve dynamic version from different repositories"() {
        given:
        def repo1 = ivyRepo("ivyRepo1")
        def repo2 = ivyRepo("ivyRepo2")

        and:
        buildFile << """
  repositories {
      ivy {
          url "${repo1.uri}"
      }
      ivy {
          url "${repo2.uri}"
      }

  }
  configurations { compile }
  dependencies {
      compile 'org.test:projectA:latest.milestone'
  }
  """
        and:
        def resolve = new ResolveTestFixture(buildFile, "compile")
        resolve.prepare()

        when:
        repo1.module('org.test', 'projectA', '1.1').withStatus("milestone").publish()
        repo2.module('org.test', 'projectA', '1.2').withStatus("integration").publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.1") {
                    notRequested()
                    byReason("didn't match version 1.2")
                }
            }
        }

        when:
        repo2.module('org.test', 'projectA', '1.3').withStatus("milestone").publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.3")
            }
        }
    }

}
