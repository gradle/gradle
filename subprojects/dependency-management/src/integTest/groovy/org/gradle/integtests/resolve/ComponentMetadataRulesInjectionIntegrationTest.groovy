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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.util.ToBeImplemented

class ComponentMetadataRulesInjectionIntegrationTest extends AbstractHttpDependencyResolutionTest implements ComponentMetadataRulesSupport {

    @ToBeImplemented("Ideally we would have a solution to provide such a service in this case")
    def 'cannot inject and use RepositoryResourceAccessor for flat dir repo'() {
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
    }
}

dependencies {
    components {
        all(AssertingRule)
    }
    conf 'org:my-lib:1.0'
}

task resolve {
    doLast {
        delete 'libs'
        copy {
            from configurations.conf
            into 'libs'
        }
    }
}
"""
        when:
        fails 'resolve'

        then:
        failure.assertHasCause('Could not create an instance of type AssertingRule.')
        failure.assertHasCause('Unable to determine AssertingRule argument #1: missing parameter value of type interface org.gradle.api.artifacts.repositories.RepositoryResourceAccessor')

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
    doLast {
        delete 'libs'
        copy {
            from configurations.conf
            into 'libs'
        }
    }
}
"""
        when:
        succeeds 'resolve'

        then:
        outputContains('AssertingRule executed')
    }
}
