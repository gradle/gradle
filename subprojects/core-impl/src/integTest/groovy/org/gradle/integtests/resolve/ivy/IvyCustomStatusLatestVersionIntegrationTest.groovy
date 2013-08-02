/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class IvyCustomStatusLatestVersionIntegrationTest extends AbstractIntegrationSpec {
    def "latest.xyz selects highest version with given or higher status"() {
        given:
        buildFile << """
repositories {
  ivy {
      url "${ivyRepo.uri}"
  }
}
configurations { compile }
dependencies {
  compile 'org.test:projectA:latest.$status'
}
componentMetadata {
  eachComponent { details ->
      details.statusScheme = ["bronze", "silver", "gold", "platin"]
  }
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""

        when:
        ivyRepo.module('org.test', 'projectA', '1.1').withStatus('bronze').publish()
        ivyRepo.module('org.test', 'projectA', '1.3').withStatus('silver').publish()
        ivyRepo.module('org.test', 'projectA', '1.2').withStatus('gold').publish()
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus('platin').publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-${version}.jar")

        where:
        status   | version
        "bronze" | "1.3"
        "silver" | "1.3"
        "gold"   | "1.2"
        "platin" | "1.0"
    }
}
