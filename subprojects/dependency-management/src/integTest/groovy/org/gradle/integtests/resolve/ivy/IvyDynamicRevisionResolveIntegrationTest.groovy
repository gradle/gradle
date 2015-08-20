/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.integtests.resolve.ResolveTestFixture
import spock.lang.Issue

class IvyDynamicRevisionResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def setup() {
        settingsFile << "rootProject.name = 'test' "
    }

    @Issue("GRADLE-2502")
    def "latest.integration selects highest version regardless of status"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'org.test:projectA:latest.integration'
  }
  """

        and:
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any matches for org.test:projectA:latest.integration as no versions of org.test:projectA are available.'

        when:
        ivyRepo.module('org.test', 'projectA', '1.0').withNoMetaData().publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.integration", "org.test:projectA:1.0")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('integration').publish()
        ivyRepo.module('org.test', 'projectA', '1.2').withStatus('integration').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.integration", "org.test:projectA:1.2")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('release').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.integration", "org.test:projectA:1.3")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.4').withNoMetaData().publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.integration", "org.test:projectA:1.4")
            }
        }
    }

    @Issue("GRADLE-2502")
    def "latest.milestone selects highest version with milestone or release status"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'org.test:projectA:latest.milestone'
  }
  """
        and:
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any matches for org.test:projectA:latest.milestone as no versions of org.test:projectA are available.'

        when:
        ivyRepo.module('org.test', 'projectA', '2.0').withNoMetaData().publish()
        runAndFail 'checkDeps'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.milestone.
Versions that do not match:
    2.0
Searched in the following locations:
'''

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('integration').publish()
        runAndFail 'checkDeps'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.milestone.
Versions that do not match:
    2.0
    1.3
Searched in the following locations:
'''

        when:
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus('milestone').publish()
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('milestone').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.1")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.2').withStatus('release').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.2")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('integration').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.2")
            }
        }
    }

    @Issue("GRADLE-2502")
    public void "latest.release selects highest version with release status"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'org.test:projectA:latest.release'
  }
  """
        and:
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any matches for org.test:projectA:latest.release as no versions of org.test:projectA are available.'

        when:
        ivyRepo.module('org.test', 'projectA', '2.0').withNoMetaData().publish()
        runAndFail 'checkDeps'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.release.
Versions that do not match:
    2.0
Searched in the following locations:
'''

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('integration').publish()
        ivyRepo.module('org.test', 'projectA', '1.2').withStatus('milestone').publish()
        runAndFail 'checkDeps'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.release.
Versions that do not match:
    2.0
    1.3
    1.2
Searched in the following locations:
'''

        when:
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus('release').publish()
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('release').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.release", "org.test:projectA:1.1")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.1.1').withStatus('milestone').publish()
        ivyRepo.module('org.test', 'projectA', '1.1-beta2').withStatus('integration').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.release", "org.test:projectA:1.1")
            }
        }
    }

    @Issue(["GRADLE-2502", "GRADLE-2794"])
    def "version selector ending in + selects highest matching version"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'org.test:projectA:1.2+'
  }
  """
        and:
        ivyRepo.module('org.test', 'projectA', '1.1.2').publish()
        ivyRepo.module('org.test', 'projectA', '2.0').publish()

        and:
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:1.2+'

        when:
        ivyRepo.module('org.test', 'projectA', '1.2').withNoMetaData().publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.2+", "org.test:projectA:1.2")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.2.1').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.2+", "org.test:projectA:1.2.1")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.2.9').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.2+", "org.test:projectA:1.2.9")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.2.10').withNoMetaData().publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.2+", "org.test:projectA:1.2.10")
            }
        }
    }

    @Issue("GRADLE-2502")
    def "version range selects highest matching version"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'org.test:projectA:[1.2,2.0]'
  }
  """
        and:
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        and:
        ivyRepo.module('org.test', 'projectA', '1.1.2').publish()
        ivyRepo.module('org.test', 'projectA', '2.1').publish()

        when:
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:[1.2,2.0]'

        when:
        ivyRepo.module('org.test', 'projectA', '1.2').withNoMetaData().publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.2,2.0]", "org.test:projectA:1.2")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.2.1').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.2,2.0]", "org.test:projectA:1.2.1")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.2,2.0]", "org.test:projectA:1.3")
            }
        }

        when:
        ivyRepo.module('org.test', 'projectA', '1.4').withNoMetaData().publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.2,2.0]", "org.test:projectA:1.4")
            }
        }
    }

    @Issue("GRADLE-3334")
    def "can resolve version range with single value specified"() {
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyRepo.uri}"
    }
}

configurations { compile }

dependencies {
    compile group: "org.test", name: "projectA", version: "[1.1]"
}
"""
        and:
        ivyRepo.module('org.test', 'projectB', '2.0').publish()
        ivyRepo.module('org.test', 'projectA', '1.1').dependsOn('org.test', 'projectB', '[2.0]').publish()

        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.1]", "org.test:projectA:1.1") {
                    edge("org.test:projectB:2.0", "org.test:projectB:2.0") // Transitive version range is lost when converting to Ivy ModuleDescriptor
                }
            }
        }
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
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        repo1.module('org.test', 'projectA', '1.1').withStatus("milestone").publish()
        repo2.module('org.test', 'projectA', '1.2').withStatus("integration").publish()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.1")
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
