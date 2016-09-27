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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class MavenDependencyResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile)

    def setup() {
        resolve.prepare()
        settingsFile << """
            rootProject.name = 'testproject'
        """
    }

    def "dependency includes main artifact and runtime dependencies of referenced module"() {
        given:
        def other = mavenRepo.module("org.gradle", "other", "preview-1").publish()
        mavenRepo.module("org.gradle", "test", "1.45")
            .dependsOn(other)
            .artifact(classifier: 'classifier') // ignored
            .publish()

        and:

        buildFile << """
group = 'org.gradle'
version = '1.0'
repositories { maven { url "${mavenRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}
"""

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', 'org.gradle:testproject:1.0') {
                module("org.gradle:test:1.45") {
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "dependency that references a classifier includes the matching artifact only plus the runtime dependencies of referenced module"() {
        given:
        def other = mavenRepo.module("org.gradle", "other", "preview-1").publish()
        mavenRepo.module("org.gradle", "test", "1.45")
            .dependsOn(other)
            .artifact(classifier: 'classifier')
            .artifact(classifier: 'some-other') // ignored
            .publish()

        and:
        buildFile << """
group = 'org.gradle'
version = '1.0'
repositories { maven { url "${mavenRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45:classifier"
}
"""

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', 'org.gradle:testproject:1.0') {
                module("org.gradle:test:1.45") {
                    artifact(classifier: 'classifier')
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "dependency that references an artifact includes the matching artifact only plus the runtime dependencies of referenced module"() {
        given:
        def other = mavenRepo.module("org.gradle", "other", "preview-1").publish()
        mavenRepo.module("org.gradle", "test", "1.45")
            .dependsOn(other)
            .artifact(type: 'aar', classifier: 'classifier')
            .publish()

        and:
        buildFile << """
group = 'org.gradle'
version = '1.0'
repositories { maven { url "${mavenRepo.uri}" } }
configurations { compile }
dependencies {
    compile ("org.gradle:test:1.45") {
        artifact {
            name = 'test'
            type = 'aar'
            classifier = 'classifier'
        }
    }
}
"""

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', 'org.gradle:testproject:1.0') {
                module("org.gradle:test:1.45") {
                    artifact(type: 'aar', classifier: 'classifier')
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "does not include optional dependencies of maven module"() {
        given:
        def notRequired = mavenRepo.module("org.gradle", "i-do-not-exist", "1.0")
        mavenRepo.module("org.gradle", "test", "1.45")
            .dependsOn(notRequired, optional: true)
            .dependsOn(notRequired, optional: true, scope: 'runtime')
            .publish()

        and:

        buildFile << """
repositories { maven { url "${mavenRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}
"""

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.gradle:test:1.45")
            }
        }
    }

    @Requires(TestPrecondition.ONLINE)
    def "resolves dependencies on real projects"() {
        // Real but ancient projects
        // Hibernate core brings in conflicts, exclusions and root poms
        // Add a direct dependency on an earlier version of commons-collection than required by hibernate core
        // Logback classic depends on a later version of slf4j-api than required by hibernate core

        given:
        buildFile << """
repositories {
    mavenCentral()
}

configurations {
    compile
}

dependencies {
    compile "commons-collections:commons-collections:3.0"
    compile "ch.qos.logback:logback-classic:0.9.30"
    compile "org.hibernate:hibernate-core:3.6.7.Final"
}

task check {
    doLast {
        def compile = configurations.compile

        def filteredDependencies = compile.resolvedConfiguration.getFirstLevelModuleDependencies({ it.name == 'logback-classic' } as Spec)
        assert filteredDependencies.collect { it.name } == [
            'ch.qos.logback:logback-classic:0.9.30'
        ]

        def filteredFiles = compile.resolvedConfiguration.getFiles({ it.name == 'logback-classic' } as Spec)
        assert filteredFiles.collect { it.name } == [
            'logback-classic-0.9.30.jar',
             'logback-core-0.9.30.jar',
              'slf4j-api-1.6.2.jar'
        ]
    }
}
"""

        expect:
        succeeds "check", "checkDep"
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('ch.qos.logback:logback-classic:0.9.30') {
                    module('ch.qos.logback:logback-core:0.9.30')
                    module('org.slf4j:slf4j-api:1.6.2')
                }
                module('org.hibernate:hibernate-core:3.6.7.Final') {
                    module('org.hibernate:hibernate-commons-annotations:3.2.0.Final') {
                        edge('org.slf4j:slf4j-api:1.5.8', 'org.slf4j:slf4j-api:1.6.2') {
                            byConflictResolution()
                        }
                    }
                    module('org.hibernate.javax.persistence:hibernate-jpa-2.0-api:1.0.1.Final')
                    module('antlr:antlr:2.7.6')
                    module('dom4j:dom4j:1.6.1')
                    module('javax.transaction:jta:1.1')
                    module('commons-collections:commons-collections:3.1')
                    edge('org.slf4j:slf4j-api:1.6.1', 'org.slf4j:slf4j-api:1.6.2') {
                        byConflictResolution()
                    }
                }
                edge('commons-collections:commons-collections:3.0', 'commons-collections:commons-collections:3.1') {
                    byConflictResolution()
                }
            }
        }
    }

    def "dependency with wildcard exclusions is treated as non-transitive"() {
        given:
        def thirdLevel = mavenRepo.module("org.gradle", "thirdLevel", "1.0").publish()
        def secondLevel = mavenRepo.module("org.gradle", "secondLevel", "1.0").dependsOn(thirdLevel).publish()
        def firstLevel = mavenRepo.module("org.gradle", "firstLevel", "1.0").dependsOn(secondLevel.groupId,
            secondLevel.artifactId, secondLevel.version, null, null, null, [[groupId: '*', artifactId: '*']]).publish()

        and:
        buildFile << """
repositories { maven { url "${mavenRepo.uri}" } }
configurations { compile }
dependencies {
    compile "$firstLevel.groupId:$firstLevel.artifactId:$firstLevel.version"
}
"""

        expect:
        succeeds "checkDep"
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.gradle:firstLevel:1.0") {
                    module("org.gradle:secondLevel:1.0")
                }
            }
        }
    }
}
