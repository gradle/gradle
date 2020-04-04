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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class ComponentMetadataRulesCachingIntegrationTest extends AbstractModuleDependencyResolveTest implements ComponentMetadataRulesSupport {
    String getDefaultStatus() {
        GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release'
    }

    def setup() {
        buildFile << """
dependencies {
    conf 'org.test:projectA:1.0'
}

// implement Sync manually to make sure that task is never up-to-date
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
        executer.withArgument("-Ddebug.modulesource=true")
    }

    @ToBeFixedForInstantExecution
    def "rule is cached across builds"() {
        repository {
            'org.test:projectA:1.0' {
                dependsOn('org.test:projectB:1.0')
            }
            'org.test:projectB:1.0'()
        }
        buildFile << """

@CacheableRule
class CachedRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            println "Rule executed on \${context.details.id}"
            context.details.allVariants {
                println("Variant \$it")
                withDependencies { deps ->
                    deps.each {
                       println "See dependency: \$it"
                    }
                }
            }
    }
}

dependencies {
    components {
        all(CachedRule)
    }
}
"""

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
            'org.test:projectB:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
        outputContains('Rule executed')
        outputContains('See dependency')


        then:
        succeeds 'resolve'
        outputDoesNotContain('Rule executed')
        outputDoesNotContain('See dependency')
    }

    @ToBeFixedForInstantExecution
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def 'cached rule can access PomModuleDescriptor for Maven component'() {
        given:
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile << """
@CacheableRule
class PomRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        assert context.getDescriptor(PomModuleDescriptor) != null
        assert context.getDescriptor(PomModuleDescriptor).packaging == "jar"
    }
}

dependencies {
    components {
        all(PomRule)
    }
}
"""
        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'resolve'
        succeeds 'resolve'
    }

    @ToBeFixedForInstantExecution
    def 'rule cache properly differentiates inputs'() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """

@CacheableRule
class CachedRuleA implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            println 'Rule A executed'
            context.details.changing = true
    }
}

@CacheableRule
class CachedRuleB implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            println 'Rule B executed - saw changing ' + context.details.changing
    }
}

dependencies {
    components {
        if (project.hasProperty('cacheA')) {
            all(CachedRuleA)
        }
        all(CachedRuleB)
    }
}
"""
        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
        outputContains('Rule B executed - saw changing false')


        then:
        succeeds 'resolve', '-PcacheA'
        outputContains('Rule A executed')
        outputContains('Rule B executed - saw changing true')
    }

    @ToBeFixedForInstantExecution
    def 'can cache rules with service injection'() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """

import javax.inject.Inject
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor

@CacheableRule
class CachedRuleA implements ComponentMetadataRule {

    RepositoryResourceAccessor accessor

    @Inject
    CachedRuleA(RepositoryResourceAccessor accessor) {
        this.accessor = accessor
    }

    void execute(ComponentMetadataContext context) {
            println 'Rule A executed'
            context.details.changing = true
    }
}

@CacheableRule
class CachedRuleB implements ComponentMetadataRule {

    RepositoryResourceAccessor accessor

    @Inject
    CachedRuleB(RepositoryResourceAccessor accessor) {
        this.accessor = accessor
    }

    public void execute(ComponentMetadataContext context) {
            println 'Rule B executed - saw changing ' + context.details.changing
    }
}

dependencies {
    components {
        if (project.hasProperty('cacheA')) {
            all(CachedRuleA)
        }
        all(CachedRuleB)
    }
}
"""
        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
        outputContains('Rule B executed - saw changing false')


        and:
        succeeds 'resolve', '-PcacheA'
        outputContains('Rule A executed')
        outputContains('Rule B executed - saw changing true')
    }

    @ToBeFixedForInstantExecution
    def 'can cache rules having a custom type attribute as parameter'() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """

import javax.inject.Inject

@CacheableRule
class AttributeCachedRule implements ComponentMetadataRule {

    Attribute targetAttribute

    @Inject
    AttributeCachedRule(Attribute attribute) {
        this.targetAttribute = attribute
    }

    void execute(ComponentMetadataContext context) {
        println 'Attribute rule executed'
    }
}

interface Thing extends Named { }

def thing = Attribute.of(Thing)

dependencies {
    components {
        all(AttributeCachedRule) {
            params(thing)
        }
    }
}
"""
        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
        outputContains('Attribute rule executed')

        and:
        succeeds 'resolve'
        outputDoesNotContain('Attribute rule executed')
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @ToBeFixedForInstantExecution
    def 'can cache rules setting custom type attributes'() {
        repository {
            'org.test:projectA:1.0'()
        }

        def expectedStatus = useIvy() ? 'integration' : 'release'

        buildFile << """

import javax.inject.Inject

@CacheableRule
class AttributeCachedRule implements ComponentMetadataRule {

    ObjectFactory objects
    Attribute targetAttribute

    @Inject
    AttributeCachedRule(ObjectFactory objects, Attribute attribute) {
        this.objects = objects
        this.targetAttribute = attribute
    }

    void execute(ComponentMetadataContext context) {
        println 'Attribute rule executed'
        context.details.withVariant('api') {
            attributes {
                attribute(targetAttribute, objects.named(Thing, 'Foo'))
            }
        }
        context.details.withVariant('runtime') {
            attributes {
                attribute(targetAttribute, objects.named(Thing, 'Bar'))
            }
        }
        context.details.withVariant('foo') {
            attributes {
                attribute(targetAttribute, objects.named(Thing, 'Bar'))
            }
        }
    }
}

interface Thing extends Named { }

def thing = Attribute.of(Thing)

configurations {
    conf {
        attributes {
            attribute thing, objects.named(Thing, 'Bar')
        }
    }
}

dependencies {
    components {
        all(AttributeCachedRule) {
            params(thing)
        }
    }
}
"""
        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'checkDeps'
        outputContains('Attribute rule executed')
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.test:projectA:1.0') {
                    variant('runtime', ['org.gradle.status': expectedStatus, 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', 'thing': 'Bar'])
                }
            }
        }


        and:
        succeeds 'checkDeps'
        outputDoesNotContain('Attribute rule executed')
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.test:projectA:1.0') {
                    variant('runtime', ['org.gradle.status': expectedStatus, 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', 'thing': 'Bar'])
                }
            }
        }
    }

    def 'changing rule implementation invalidates cache'() {
        repository {
            'org.test:projectA:1.0'()
        }

        def cachedRule = file('buildSrc/src/main/groovy/rule/CachedRule.groovy')
        cachedRule.text = """
package rule

import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext

@CacheableRule
class CachedRule implements ComponentMetadataRule {

    void execute(ComponentMetadataContext context) {
        println 'Cached rule executed'
    }
}
"""

        buildFile << """
import rule.CachedRule

dependencies {
    conf 'org.test:projectA:1.0'
    components {
        all(CachedRule)
    }
}
"""
        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'checkDeps'
        outputContains('Cached rule executed')

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }
        cachedRule.text = """
package rule

import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext

@CacheableRule
class CachedRule implements ComponentMetadataRule {

    void execute(ComponentMetadataContext context) {
        println 'Modified cached rule executed'
    }
}
"""

        then:
        succeeds 'checkDeps'
        outputContains('Modified cached rule executed')

    }
}
