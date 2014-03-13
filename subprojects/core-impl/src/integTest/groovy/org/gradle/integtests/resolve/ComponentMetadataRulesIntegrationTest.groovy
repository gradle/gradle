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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.HttpRepository

abstract class ComponentMetadataRulesIntegrationTest extends AbstractDependencyResolutionTest {
    abstract HttpRepository getRepo()
    abstract String getRepoDeclaration()
    abstract String getDefaultStatus()

    def setup() {
        server.start()

        buildFile <<
"""
$repoDeclaration

configurations { compile }

dependencies {
    compile 'org.test:projectA:1.0'
}

task resolve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
    }


    def "rule receives correct metadata"() {
        repo.module('org.test', 'projectA', '1.0').publish().allowAll()
        buildFile <<
"""
dependencies {
    components {
        eachComponent { details ->
            assert details.id.group == "org.test"
            assert details.id.name == "projectA"
            assert details.id.version == "1.0"
            assert details.status == "$defaultStatus"
            assert details.statusScheme == ["integration", "milestone", "release"]
            assert !details.changing
        }
    }
}
"""

        expect:
        succeeds 'resolve'
    }

    def "changes made by a rule are visible to subsequent rules"() {
        repo.module('org.test', 'projectA', '1.0').publish().allowAll()

        buildFile <<
                """
dependencies {
    components {
        eachComponent { details ->
            details.status "integration.changed" // verify that 'details' is enhanced
            details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
            details.changing = true
        }
        eachComponent { details ->
            assert details.status == "integration.changed"
            assert details.statusScheme == ["integration.changed", "milestone.changed", "release.changed"]
            assert details.changing
        }
    }
}
"""

        expect:
        succeeds 'resolve'
    }

    def "changes made by a rule are not cached"() {
        repo.module('org.test', 'projectA', '1.0').publish().allowAll()

        buildFile <<
                """
dependencies {
    components {
        eachComponent { details ->
            assert !details.changing
            assert details.status == "$defaultStatus"
            assert details.statusScheme == ["integration", "milestone", "release"]

            details.changing = true
            details.status = "release.changed"
            details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
        }
    }
}
"""

        expect:
        succeeds 'resolve'
        succeeds 'resolve'
    }
}
