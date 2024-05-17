/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class ComponentMetadataRulesInjectionIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def 'inject no-op RepositoryResourceAccessor for flat dir repo'() {
        file('lib', 'my-lib-1.0.jar').createFile()
        buildFile << """
repositories {
    flatDir {
        dirs 'lib'
    }
}

configurations {
    conf
}

class AssertingRule implements ComponentMetadataRule {
    RepositoryResourceAccessor accessor

    @javax.inject.Inject
    public AssertingRule(RepositoryResourceAccessor accessor) {
        this.accessor = accessor
    }
    public void execute(ComponentMetadataContext context) {
        println 'AssertingRule executed'
        accessor.withResource('my-lib-1.0.jar') {
            println 'Resource action executed'
        }
    }
}

dependencies {
    components {
        all(AssertingRule)
    }
    conf 'org:my-lib:1.0'
}

task resolve {
    def files = configurations.conf
    doLast {
        files.forEach { }
    }
}
"""
        when:
        succeeds 'resolve'

        then:
        outputContains('AssertingRule executed')
        outputDoesNotContain('Resource action executed')
    }

    def 'can inject and use RepositoryResourceAccessor for ivy local repo'() {
        ivyRepo.module("org", "my-lib", "1.0").publish()

        buildFile << """
repositories {
    ivy { url "${ivyRepo.uri}" }
}

configurations {
    conf
}

class AssertingRule implements ComponentMetadataRule {
    RepositoryResourceAccessor accessor

    @javax.inject.Inject
    public AssertingRule(RepositoryResourceAccessor accessor) {
        this.accessor = accessor
    }
    public void execute(ComponentMetadataContext context) {
        println 'AssertingRule executed'
        accessor.withResource('org/my-lib/1.0/ivy.xml') {
            assert it.available() != 0
        }
    }
}

dependencies {
    components {
        all(AssertingRule)
    }
    conf 'org:my-lib:1.0'
}

task resolve {
    def files = configurations.conf
    doLast {
        files.forEach { }
    }
}
"""
        when:
        succeeds 'resolve'

        then:
        outputContains('AssertingRule executed')
    }

    def 'can inject and use RepositoryResourceAccessor for maven local repo'() {
        using m2
        m2.mavenRepo().module("org", "my-lib", "1.0").publish()

        buildFile << """
repositories {
    mavenLocal()
}

configurations {
    conf
}

class AssertingRule implements ComponentMetadataRule {
    RepositoryResourceAccessor accessor

    @javax.inject.Inject
    public AssertingRule(RepositoryResourceAccessor accessor) {
        this.accessor = accessor
    }
    public void execute(ComponentMetadataContext context) {
        println 'AssertingRule executed'
        accessor.withResource('org/my-lib/1.0/my-lib-1.0.pom') {
            assert it.available() != 0
        }
    }
}

dependencies {
    components {
        all(AssertingRule)
    }
    conf 'org:my-lib:1.0'
}

task resolve {
    def files = configurations.conf
    doLast {
        files.forEach { }
    }
}
"""
        when:
        succeeds 'resolve'

        then:
        outputContains('AssertingRule executed')
    }
}
