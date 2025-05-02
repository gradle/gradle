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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

import static org.hamcrest.CoreMatchers.containsString

class IvyDescriptorValidationIntegrationTest extends AbstractDependencyResolutionTest {
    @ToBeFixedForConfigurationCache
    def "incorrect value for revision attribute is flagged"() {
        given:
        buildFile << """
  repositories {
      ivy {
          url = "${ivyRepo.uri}"
      }
  }
  configurations { compile }
  dependencies {
      compile 'org.test:projectA:[1.3,1.5]'
  }
  task resolve {
      doLast {
          configurations.compile.resolve()
      }
  }
  """

        def module = ivyRepo.module('org.test', 'projectA', '1.4')
        module.publish()

        expect:
        succeeds 'resolve'

        when:
        module.ivyFile.setText(module.ivyFile.text.replace("revision='1.4'", "revision='1.6'"), "utf-8")

        then:
        fails 'resolve'
        failure.assertThatCause(containsString("bad version: expected='1.4' found='1.6'"))
    }

}
