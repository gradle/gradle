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
import spock.lang.Ignore
import spock.lang.Issue

class IvyDynamicRevisionResolveIntegrationTest extends AbstractDependencyResolutionTest {
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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:latest.integration.'

        when:
        ivyRepo.module('org.test', 'projectA', '1.0').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')

        when:
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('integration').publish()
        ivyRepo.module('org.test', 'projectA', '1.2').withStatus('integration').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('release').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.3.jar')
    }

    @Ignore
    @Issue("GRADLE-2502")
    def "latest.integration selects highest version regardless of status even if metadata is missing"() {
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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """

        when:
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('integration').publish()
        ivyRepo.module('org.test', 'projectA', '1.4').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.4.jar')
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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """
        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:latest.milestone.'

        when:
        ivyRepo.module('org.test', 'projectA', '2.0').withNoMetaData().publish()
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:latest.milestone.'

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('integration').publish()
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:latest.milestone.'

        when:
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus('milestone').publish()
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('milestone').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')

        when:
        ivyRepo.module('org.test', 'projectA', '1.2').withStatus('release').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')
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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:latest.release.'

        when:
        ivyRepo.module('org.test', 'projectA', '2.0').withNoMetaData().publish()
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:latest.release.'

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('integration').publish()
        ivyRepo.module('org.test', 'projectA', '1.2').withStatus('milestone').publish()
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:latest.release.'

        when:
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus('release').publish()
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('release').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')
    }

    @Issue("GRADLE-2502")
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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """
        and:
        ivyRepo.module('org.test', 'projectA', '1.1.2').publish()
        ivyRepo.module('org.test', 'projectA', '2.0').publish()

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:1.2+'

        when:
        ivyRepo.module('org.test', 'projectA', '1.2').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')

        when:
        ivyRepo.module('org.test', 'projectA', '1.2.1').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.1.jar')

        when:
        ivyRepo.module('org.test', 'projectA', '1.2.9').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.9.jar')
    }

    @Ignore
    @Issue("GRADLE-2502")
    def "version selector ending in + selects highest matching version even if metadata is missing"() {
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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """
        and:
        ivyRepo.module('org.test', 'projectA', '1.2.1').publish()
        ivyRepo.module('org.test', 'projectA', '2.0').publish()

        when:
        ivyRepo.module('org.test', 'projectA', '1.2.12').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.12.jar')
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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """
        and:
        ivyRepo.module('org.test', 'projectA', '1.1.2').publish()
        ivyRepo.module('org.test', 'projectA', '2.1').publish()

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:[1.2,2.0]'

        when:
        ivyRepo.module('org.test', 'projectA', '1.2').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')

        when:
        ivyRepo.module('org.test', 'projectA', '1.2.1').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.1.jar')

        when:
        ivyRepo.module('org.test', 'projectA', '1.3').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.3.jar')
    }

    @Ignore
    @Issue("GRADLE-2502")
    def "version range selects highest matching version even if metadata is missing"() {
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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """
        and:
        ivyRepo.module('org.test', 'projectA', '1.2.1').publish()

        when:
        ivyRepo.module('org.test', 'projectA', '1.3.12').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.3.12.jar')
    }

    @Issue("GRADLE-2502")
    def "can resolve dynamic version from different repositories"() {
        given:
        def repo1 = ivyRepo("ivyRepo1")
        def repo2 = ivyRepo("ivyRepo2")
        repo1.module('org.test', 'projectA', '1.1').withStatus("milestone").publish()
        repo2.module('org.test', 'projectA', '1.2').withStatus("integration").publish()

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
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')
    }

    def "can resolve dynamic versions with multiple ivy patterns"() {
        given:
        def repo1 = ivyRepo("ivyRepo1")
        repo1.module('org.test', 'projectA', '1.1').withStatus("integration").publish()
        repo1.module('org.test', 'projectA', '1.2').withStatus("milestone").publish()
        def repo2 = ivyRepo("ivyRepo2")
        repo2.module('org.test', 'projectA', '1.1').withStatus("milestone").publish()
        repo2.module('org.test', 'projectA', '1.3').withStatus("integration").publish()

        and:
        buildFile << """
repositories {
    ivy {
        url "${repo1.uri}"
        ivyPattern "${repo2.uri}/[organisation]/[module]/[revision]/ivy-[revision].xml"
        artifactPattern "${repo2.uri}/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
configurations {
    milestone
    dynamic
}
dependencies {
  milestone 'org.test:projectA:latest.milestone'
  dynamic 'org.test:projectA:1.+'
}
task retrieveDynamic(type: Sync) {
  from configurations.dynamic
  into 'dynamic'
}
task retrieveMilestone(type: Sync) {
  from configurations.milestone
  into 'milestone'
}
"""

        when:
        run 'retrieveDynamic'

        then:
        file('dynamic').assertHasDescendants('projectA-1.3.jar')

        when:
        run 'retrieveMilestone'

        then:
        file('milestone').assertHasDescendants('projectA-1.2.jar')
    }
}
