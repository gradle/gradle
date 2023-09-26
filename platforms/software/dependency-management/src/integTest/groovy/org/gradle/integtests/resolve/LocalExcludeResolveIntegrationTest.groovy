/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Issue

@FluidDependenciesResolveTest
class LocalExcludeResolveIntegrationTest extends AbstractDependencyResolutionTest {
    /**
     * Dependency exclude rules defined through Gradle DSL.
     *
     * Dependency graph:
     *
     * org.gradle:test:1.45 -> org.gradle:foo:2.0, org.gradle:bar:3.0, com.company:company:4.0, com.company:other-company:4.0
     * com.company:company:4.0 -> com.enterprise:enterprise:5.0, org.gradle:baz:6.0
     */
    def "dependency exclude rule for #condition"() {
        given:
        final String orgGradleGroupId = 'org.gradle'
        def testModule = mavenRepo().module(orgGradleGroupId, 'test', '1.45')
        def fooModule = mavenRepo().module(orgGradleGroupId, 'foo', '2.0')
        fooModule.publish()
        def barModule = mavenRepo().module(orgGradleGroupId, 'bar', '3.0')
        barModule.publish()
        def bazModule = mavenRepo().module(orgGradleGroupId, 'baz', '6.0')
        bazModule.publish()

        def enterpriseModule = mavenRepo().module('com.enterprise', 'enterprise', '5.0')
        enterpriseModule.publish()

        def companyModule = mavenRepo().module('com.company', 'company', '4.0')
        companyModule.dependsOn(enterpriseModule)
        companyModule.dependsOn(bazModule)
        companyModule.publish()

        def otherCompanyModule = mavenRepo().module('com.company', 'other-company', '4.0')
        otherCompanyModule.publish()

        testModule.dependsOn(fooModule)
        testModule.dependsOn(barModule)
        testModule.dependsOn(companyModule)
        testModule.dependsOn(otherCompanyModule)
        testModule.publish()

        and:
        buildFile << """
repositories { maven { url "${mavenRepo().uri}" } }
configurations { compile }
dependencies {
    compile('${testModule.groupId}:${testModule.artifactId}:${testModule.version}') {
        exclude ${excludeAttributes.collect { key, value -> "$key: '$value'" }.join(', ')}
    }
}

task check {
    def files = configurations.compile
    doLast {
        assert files.collect { it.name } == [${resolvedJars.collect { "'$it'" }.join(", ")}]
    }
}
"""

        expect:
        succeeds "check"

        where:
        condition                                          | excludeAttributes                         | resolvedJars
        'excluding by group'                               | [group: 'com.company']                    | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar']
        'excluding by module and group'                    | [group: 'com.company', module: 'company'] | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'other-company-4.0.jar']
        'excluding group of declared module'               | [group: 'org.gradle']                     | ['test-1.45.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar']
        'excluding other module in same group as declared' | [group: 'org.gradle', module: 'foo']      | ['test-1.45.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar', 'baz-6.0.jar']
        'excluding transitive module by group'             | [group: 'com.enterprise']                 | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'baz-6.0.jar']
        'non-matching group attribute'                     | [group: 'some.other']                     | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar', 'baz-6.0.jar']
        'non-matching module attribute'                    | [module: 'unknown']                       | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar', 'baz-6.0.jar']
        'attempting to exclude declared module'            | [group: 'org.gradle', module: 'test']     | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'company-4.0.jar', 'other-company-4.0.jar', 'enterprise-5.0.jar', 'baz-6.0.jar']
    }

    void "does not resolve module excluded for configuration"() {
        given:
        def repo = mavenRepo
        repo.module('org.gradle.test', 'direct', '1.0').publish()
        repo.module('org.gradle.test', 'transitive', '1.0').publish()
        def module = repo.module('org.gradle.test', 'external', '1.0')
        module.dependsOn('org.gradle.test', 'transitive', '1.0')
        module.publish()

        buildFile << """
repositories {
    maven { url '${repo.uri}' }
}
configurations {
    excluded {
        exclude module: 'direct'
        exclude module: 'transitive'
    }
    extendedExcluded.extendsFrom excluded
}
dependencies {
    excluded 'org.gradle.test:external:1.0'
    excluded 'org.gradle.test:direct:1.0'
}

task test {
    def excluded = configurations.excluded
    def extendedExcluded = configurations.extendedExcluded
    doLast {
        assert excluded*.name == ['external-1.0.jar']
        assert extendedExcluded*.name == ['external-1.0.jar']
    }
}
"""

        expect:
        succeeds 'test'
    }


    /**
     * Dependency graph:
     *
     * org.gradle:test:1.0
     * +--- org.foo:foo:2.0
     *      \--- org.bar:bar:3.0
     */
    @Issue("gradle/gradle#951")
    def "can declare fine-grained transitive dependency #condition"() {
        given:
        def testModule = mavenRepo().module('org.gradle', 'test', '1.0')
        def fooModule = mavenRepo().module('org.foo', 'foo', '2.0')
        def barModule = mavenRepo().module('org.bar', 'bar', '3.0')
        barModule.publish()
        fooModule.dependsOn(barModule).publish()
        testModule.dependsOn(fooModule).publish()

        buildFile << """
repositories { maven { url "${mavenRepo().uri}" } }

configurations { compile }

dependencies {
    compile module('${testModule.groupId}:${testModule.artifactId}:${testModule.version}') {
        dependency('${fooModule.groupId}:${fooModule.artifactId}:${fooModule.version}') ${includeBar ? "" : "{ exclude module: '${barModule.artifactId}'}"}
    }
}

task check {
    def files = configurations.compile
    doLast {
        assert files.collect { it.name } == [${expectedJars.collect { "'${it}.jar'" }.join(", ")}]
    }
}
"""
        expect:
        succeeds "check"

        where:
        condition                | includeBar | expectedJars
        'include bar dependency' | true       | ['test-1.0', 'foo-2.0', 'bar-3.0']
        'exclude bar dependency' | false      | ['test-1.0', 'foo-2.0']
    }

    def "configuration excludes are supported for project dependency"() {
        given:
        mavenRepo.module('org.gradle.test', 'direct', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'transitive', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'external', '1.0')
            .dependsOn('org.gradle.test', 'transitive', '1.0')
            .publish()

        createDirs("a", "b", "c")
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b', 'c'
"""
        buildFile << """
            allprojects {
                apply plugin: 'java'
                repositories { maven { url "${mavenRepo.uri}" } }
            }

            project(':a') {
                configurations {
                    implementation {
                        exclude module: 'direct'
                        exclude module: 'transitive'
                    }
                    other {
                        exclude module: 'external'
                    }
                }
                dependencies {
                    implementation 'org.gradle.test:external:1.0'
                    implementation 'org.gradle.test:direct:1.0'
                    implementation project(':b')
                }
            }

            project(':b') {
                configurations {
                    implementation {
                        exclude module: 'external' // Only applies to transitive dependencies of 'b'
                    }
                }
            }

            dependencies {
                implementation project(':a')
            }

            def compare(config, expectedDependencies) {
                assert config*.name as Set == expectedDependencies as Set
            }

            task checkDeps {
                def runtimeClasspath = configurations.runtimeClasspath
                doLast {
                    assert runtimeClasspath*.name == ['a.jar', 'external-1.0.jar', 'b.jar']
                }
            }
"""

        expect:
        succeeds ":checkDeps"
    }


    @Issue("GRADLE-3124")
    void "provides reasonable error message for typo in exclude declaration"() {
        given:
        mavenRepo.module('org.gradle.test', 'external', '1.0').publish()

        when:
        buildFile << """
            configurations {
                foo {
                    exclude group: 'org.gradle.test', modue: 'external'
                }
            }
            dependencies {
                foo "org.gradle.test:external:1.0"
            }

            task resolve() {
                def files = configurations.foo
                doLast {
                    files.files
                }
            }
        """

        then:
        fails "resolve"
        failure.assertHasCause("Could not set unknown property 'modue' for object of type org.gradle.api.internal.artifacts.DefaultExcludeRule.")
    }

    void "makes no attempt to resolve an excluded dependency"() {
        given:
        mavenRepo.module('org.gradle.test', 'external', '1.0')
            .dependsOn('org.gradle.test', 'unknown1', '1.0')
            .dependsOn('org.gradle.test', 'unknown2', '1.0').publish()

        when:
        buildFile << """
repositories {
    maven { url '${mavenRepo.uri}' }
}
configurations {
    excluded {
        exclude module: 'unknown2'
    }
}
dependencies {
    excluded 'org.gradle.test:external:1.0', { exclude module: 'unknown1' }
    excluded 'org.gradle.test:unknown2:1.0'
}

def checkDeps(config, expectedDependencies) {
    assert config*.name as Set == expectedDependencies as Set
}

task test {
    def files = configurations.excluded
    doLast {
        assert files.collect { it.name } == ['external-1.0.jar']
    }
}
"""
        then:
        succeeds("test")
    }

}
