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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import spock.lang.Ignore
import spock.lang.Issue

class IvyDynamicRevisionResolveIntegrationTest extends AbstractDependencyResolutionTest {
    @Ignore
    @Issue("GRADLE-2502")
    public void "latest.integration selects highest version regardless of status"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'group:projectA:latest.integration'
  }
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:latest.integration.'

        when:
        ivyRepo.module('group', 'projectA', '1.0').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.1').withStatus('integration').publish()
        ivyRepo.module('group', 'projectA', '1.2').withStatus('integration').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.3').withStatus('release').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.3.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.4').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.4.jar')
    }

    @Issue("GRADLE-2502")
    public void "latest.milestone selects highest version with milestone or release status"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'group:projectA:latest.milestone'
  }
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:latest.milestone.'

        when:
        ivyRepo.module('group', 'projectA', '2.0').withNoMetaData().publish()
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:latest.milestone.'

        when:
        ivyRepo.module('group', 'projectA', '1.3').withStatus('integration').publish()
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:latest.milestone.'

        when:
        ivyRepo.module('group', 'projectA', '1.0').withStatus('milestone').publish()
        ivyRepo.module('group', 'projectA', '1.1').withStatus('milestone').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.2').withStatus('release').publish()
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
      compile 'group:projectA:latest.release'
  }
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:latest.release.'

        when:
        ivyRepo.module('group', 'projectA', '2.0').withNoMetaData().publish()
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:latest.release.'

        when:
        ivyRepo.module('group', 'projectA', '1.3').withStatus('integration').publish()
        ivyRepo.module('group', 'projectA', '1.2').withStatus('milestone').publish()
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:latest.release.'

        when:
        ivyRepo.module('group', 'projectA', '1.0').withStatus('release').publish()
        ivyRepo.module('group', 'projectA', '1.1').withStatus('release').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')
    }

    @Ignore
    @Issue("GRADLE-2502")
    public void "version selector ending in + selects highest matching version"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'group:projectA:1.2+'
  }
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """
        and:
        ivyRepo.module('group', 'projectA', '1.1.2').publish()
        ivyRepo.module('group', 'projectA', '2.0').publish()

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:1.2+'

        when:
        ivyRepo.module('group', 'projectA', '1.2').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.2.1').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.1.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.2.9').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.9.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.2.12').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.12.jar')
    }

    @Ignore
    @Issue("GRADLE-2502")
    public void "version range selects highest matching version"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'group:projectA:[1.2,2.0]'
  }
  task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
  }
  """
        and:
        ivyRepo.module('group', 'projectA', '1.1.2').publish()
        ivyRepo.module('group', 'projectA', '2.0').publish()

        when:
        runAndFail 'retrieve'

        then:
        failureHasCause 'Could not find any version that matches group:group, module:projectA, version:[1.2,2.0]'

        when:
        ivyRepo.module('group', 'projectA', '1.2').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.2.1').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.1.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.3').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.3.jar')

        when:
        ivyRepo.module('group', 'projectA', '1.3.12').withNoMetaData().publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.3.12.jar')
    }

    @Issue("GRADLE-2502")
    public void "can resolve dynamic version from different repositories"() {
        given:
        def repo1 = ivyRepo("ivyRepo1")
        def repo2 = ivyRepo("ivyRepo2")
        repo1.module('group', 'projectA', '1.1').withStatus("milestone").publish()
        repo2.module('group', 'projectA', '1.2').withStatus("integration").publish()

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
      compile 'group:projectA:latest.milestone'
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
}
