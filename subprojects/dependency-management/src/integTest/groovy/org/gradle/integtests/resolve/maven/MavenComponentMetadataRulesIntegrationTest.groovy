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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.resolve.ComponentMetadataRulesIntegrationTest
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class MavenComponentMetadataRulesIntegrationTest extends ComponentMetadataRulesIntegrationTest {
    @Override
    MavenHttpRepository getRepo() {
        mavenHttpRepo
    }

    @Override
    String getRepoDeclaration() {
"""
repositories {
    maven {
        url "$repo.uri"
    }
}
"""
    }

    @Override
    String getDefaultStatus() {
        "release"
    }

    def "rule that accepts IvyModuleDescriptor isn't invoked for Maven component"() {
        def module = repo.module('org.test', 'projectA', '1.0').publish()
        module.pom.expectGet()
        module.artifact.expectGet()

        buildFile <<
"""
def plainRuleInvoked = false
def ivyRuleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details ->
            plainRuleInvoked = true
        }
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ivyRuleInvoked = true
        }
    }
}

resolve.doLast {
    assert plainRuleInvoked
    assert !ivyRuleInvoked
}
"""

        expect:
        succeeds 'resolve'
        // also works when already cached
        succeeds 'resolve'
    }
}
