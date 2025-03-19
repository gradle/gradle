/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.resolve.rules.ComponentMetadataRulesStatusIntegrationTest
import org.gradle.test.fixtures.server.http.IvyHttpRepository

class IvyComponentMetadataRulesStatusIntegrationTest extends ComponentMetadataRulesStatusIntegrationTest {
    @Override
    IvyHttpRepository getRepo() {
        ivyHttpRepo
    }

    @Override
    String getRepoDeclaration() {
"""
repositories {
    ivy {
        url = "$ivyHttpRepo.uri"
    }
}
"""
    }

    def setup() {
        repo.module('org.test', 'projectA', '1.0').withStatus("silver").publish().allowAll()
    }

    def "module with custom status can be resolved by adapting status scheme"() {
        buildFile <<
                """
class StatusRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        assert context.details.status == "silver"
        context.details.statusScheme = ["gold", "silver", "bronze"]
    }
}
dependencies {
    components {
        all(StatusRule)
    }
}
"""

        expect:
        succeeds 'resolve'
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "resolve fails if status doesn't match default status scheme"() {
        expect:
        fails 'resolve'
        failure.assertHasCause(/Unexpected status 'silver' specified for org.test:projectA:1.0. Expected one of: [integration, milestone, release]/)
    }

    def "resolve fails if status doesn't match custom status scheme"() {
        buildFile <<
                """
class StatusRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        assert context.details.status == "silver"
        context.details.statusScheme = ["gold", "bronze"]
    }
}
dependencies {
    components {
        all(StatusRule)
    }
}
"""

        expect:
        fails 'resolve'
        failure.assertHasCause(/Unexpected status 'silver' specified for org.test:projectA:1.0. Expected one of: [gold, bronze]/)
    }

    def "rule can change status"() {
        buildFile <<
                """
class StatusRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        context.details.status = "milestone"
    }
}
dependencies {
    components {
        all(StatusRule)
    }
}
"""

        expect:
        succeeds 'resolve'
    }
}
