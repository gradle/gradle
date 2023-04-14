/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class MavenRealProjectsDependencyResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile, "compile")

    def setup() {
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        settingsFile << """
            rootProject.name = 'testproject'
        """
    }

    @Requires(TestPrecondition.ONLINE)
    def "resolves dependencies on real projects"() {
        // Real but ancient projects
        // Hibernate core brings in conflicts, exclusions and root poms
        // Add a direct dependency on an earlier version of commons-collection than required by hibernate core
        // Logback classic depends on a later version of slf4j-api than required by hibernate core

        given:
        buildFile << """
${mavenCentralRepository()}

configurations {
    compile
}

dependencies {
    compile "commons-collections:commons-collections:3.0"
    compile "ch.qos.logback:logback-classic:0.9.30"
    compile "org.hibernate:hibernate-core:3.6.7.Final"
}
"""

        expect:
        succeeds "checkDep"
        resolve.expectDefaultConfiguration('runtime')
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('ch.qos.logback:logback-classic:0.9.30') {
                    module('ch.qos.logback:logback-core:0.9.30')
                    module('org.slf4j:slf4j-api:1.6.2')
                }
                module('org.hibernate:hibernate-core:3.6.7.Final') {
                    module('org.hibernate:hibernate-commons-annotations:3.2.0.Final') {
                        edge('org.slf4j:slf4j-api:1.5.8', 'org.slf4j:slf4j-api:1.6.2') {
                            byConflictResolution("between versions 1.6.2, 1.6.1 and 1.5.8")
                        }
                    }
                    module('org.hibernate.javax.persistence:hibernate-jpa-2.0-api:1.0.1.Final')
                    module('antlr:antlr:2.7.6')
                    module('dom4j:dom4j:1.6.1')
                    module('javax.transaction:jta:1.1')
                    module('commons-collections:commons-collections:3.1')
                    edge('org.slf4j:slf4j-api:1.6.1', 'org.slf4j:slf4j-api:1.6.2') {
                        byConflictResolution("between versions 1.6.2, 1.6.1 and 1.5.8")
                    }
                }
                edge('commons-collections:commons-collections:3.0', 'commons-collections:commons-collections:3.1') {
                    byConflictResolution("between versions 3.1 and 3.0")
                }
            }
        }
    }
}
