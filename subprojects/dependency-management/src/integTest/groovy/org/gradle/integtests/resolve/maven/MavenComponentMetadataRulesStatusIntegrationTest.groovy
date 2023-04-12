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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.resolve.rules.ComponentMetadataRulesStatusIntegrationTest
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class MavenComponentMetadataRulesStatusIntegrationTest extends ComponentMetadataRulesStatusIntegrationTest {
    MavenHttpRepository getRepo() {
        mavenHttpRepo
    }

    String getRepoDeclaration() {
"""
repositories {
    maven {
        url "$repo.uri"
    }
}
"""
    }

    def "snapshot and release versions have correct status"() {
        given:
        repo.module('group1', 'projectA', '1.0').publish().allowAll()
        repo.module('group2', 'projectB', '2.0-SNAPSHOT').publish().allowAll()
        def c = repo.module('group2', 'projectC', '12-SNAPSHOT').publish().allowAll()

        and:
        buildFile.text =
"""
$repoDeclaration
configurations { compile }
class StatusRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            assert context.details.status == context.details.id.name == "projectA" ? "release" : "integration"
            assert context.details.statusScheme == ['integration', 'milestone', 'release']
    }
}
dependencies {
    compile 'group1:projectA:1.0'
    compile 'group2:projectB:2.0-SNAPSHOT'
    compile 'group2:projectC:${c.publishArtifactVersion}'
    components {
        all(StatusRule)
    }
}

task resolve {
    def files = configurations.compile
    doLast {
        files.files
    }
}
"""

        expect:
        succeeds "resolve"
    }
}
