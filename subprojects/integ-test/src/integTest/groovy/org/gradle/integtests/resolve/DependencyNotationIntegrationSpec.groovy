/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec

/**
 * by Szczepan Faber, created at: 11/9/11
 */
class DependencyNotationIntegrationSpec extends AbstractIntegrationSpec {

    def "understands dependency notations"() {
        when:
        settingsFile << "include 'otherProject'"

        buildFile <<  """
import org.gradle.api.internal.artifacts.dependencies.*
configurations { foo }

def someDependency = new DefaultSelfResolvingDependency(files('foo.txt'))
dependencies {
    foo someDependency
    foo "org.mockito:mockito-core:1.8"
    foo group: 'org.spockframework', name: 'spock-core', version: '1.0'
    foo project(':otherProject')
    foo gradleApi()
}

task checkDeps << {
    def deps = configurations.foo.incoming.dependencies
    assert deps.contains(someDependency)
    assert deps.find { it.group == 'org.mockito' && it.name == 'mockito-core' && it.version == '1.8'  }
    assert deps.find { it.group == 'org.spockframework' && it.name == 'spock-core' && it.version == '1.0'  }
    assert deps.findAll { it instanceof SelfResolvingDependency }.size() > 1 : "should include gradle api jars"
}
"""
        then:
        succeeds 'checkDeps'
    }
}
