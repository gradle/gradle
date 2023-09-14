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
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Issue

class ComponentMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest  {
    String getDefaultStatus() {
        GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release'
    }

    def setup() {
        buildFile <<
            """
dependencies {
    conf 'org.test:projectA:1.0'
}

task resolve(type: Sync) {
    def files = configurations.conf
    from files
    into 'libs'
}
"""
    }

    def "rule receives correct metadata"() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """

class AssertingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            assert context.details.id.group == "org.test"
            assert context.details.id.name == "projectA"
            assert context.details.id.version == "1.0"
            assert context.details.status == "$defaultStatus"
            assert context.details.statusScheme == ["integration", "milestone", "release"]
            assert !context.details.changing
    }
}

dependencies {
    components {
        all(AssertingRule)
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
    }

    def "rule can use artifactSelector to check sourced metadata"() {
        repository {
            'org.test:projectA:1.0' {
                dependsOn group: 'org.test', artifact: 'projectB', version: '1.0', 'classifier': 'classy'
            }
            'org.test:projectB:1.0' {
                withModule {
                    undeclaredArtifact(type: 'jar', classifier: 'classy')
                }
            }
        }
        buildFile << """

class AssertingRule implements ComponentMetadataRule {

    void execute(ComponentMetadataContext context) {
        context.details.allVariants {
            withDependencies {
                it.each { dep ->
                    println "selectorSize:" + dep.artifactSelectors.size
                    println "classifier:" + dep.artifactSelectors[0]?.classifier
                    println "type:" + dep.artifactSelectors[0]?.type
                }
            }
        }
    }
}

dependencies {
    components {
        all(AssertingRule)
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
        outputContains("selectorSize:1")
        outputContains("classifier:classy")
        outputContains("type:jar")
    }

    def "added dependency has no artifact selectors"() {
        repository {
            'org.test:projectA:1.0' {
            }
            'org.test:projectB:1.0' {
                withModule {
                    undeclaredArtifact(type: 'jar', classifier: 'classy')
                }
            }
        }
        buildFile << """

class AssertingRule implements ComponentMetadataRule {

    void execute(ComponentMetadataContext context) {
        context.details.allVariants {
            withDependencies {
                add("org.test:projectB:1.0") {
                    artifactSelectors == []
                }
            }
        }
    }
}

dependencies {
    components {
        all(AssertingRule)
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
    }

    def "changes made by a rule are visible to subsequent rules"() {
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile <<
            """
class UpdatingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            context.details.status "integration.changed" // verify that 'details' is enhanced
            context.details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
            context.details.changing = true
    }
}

class VerifyingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            assert context.details.status == "integration.changed"
            assert context.details.statusScheme == ["integration.changed", "milestone.changed", "release.changed"]
            assert context.details.changing
    }
}

dependencies {
    components {
        all(UpdatingRule)
        all(VerifyingRule)
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
    }

    def "changes made by a rule are not cached"() {
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile <<
            """
class UpdatingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            assert !context.details.changing
            assert context.details.status == "$defaultStatus"
            assert context.details.statusScheme == ["integration", "milestone", "release"]

            context.details.changing = true
            context.details.status = "release.changed"
            context.details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
    }
}

dependencies {
    components {
        all(UpdatingRule)
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
        succeeds 'resolve'
    }

    def "can apply all rule types to all modules"() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """
            ext.rulesInvoked = []

            class VerifyingRule implements ComponentMetadataRule {
                static boolean ruleInvoked

                public void execute(ComponentMetadataContext context) {
                    ruleInvoked = true
                }
            }

            dependencies {
                components {
                    all { ComponentMetadataDetails details ->
                        rulesInvoked << details.id.version
                    }
                    all {
                        rulesInvoked << id.version
                    }
                    all { details ->
                        rulesInvoked << details.id.version
                    }
                    all(new ActionRule('rulesInvoked': rulesInvoked))
                    all(new RuleObject('rulesInvoked': rulesInvoked))
                    all(VerifyingRule)
                }
            }

            class ActionRule implements Action<ComponentMetadataDetails> {
                List rulesInvoked

                void execute(ComponentMetadataDetails details) {
                    rulesInvoked << details.id.version
                }
            }

            class RuleObject {
                List rulesInvoked

                @org.gradle.model.Mutate
                void execute(ComponentMetadataDetails details) {
                    rulesInvoked << details.id.version
                }
            }

            def rules1 = provider { rulesInvoked }
            resolve.doLast {
                assert rules1.get() == [ '1.0', '1.0', '1.0', '1.0', '1.0' ]
                assert VerifyingRule.ruleInvoked
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
    }

    def "can apply all rule types by module"() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """
            ext.rulesInvoked = []
            ext.rulesUninvoked = []

            class InvokedRule implements ComponentMetadataRule {
                static boolean ruleInvoked

                public void execute(ComponentMetadataContext context) {
                    ruleInvoked = true
                }
            }

            class NotInvokedRule implements ComponentMetadataRule {
                static boolean ruleInvoked

                public void execute(ComponentMetadataContext context) {
                    ruleInvoked = true
                }
            }

            dependencies {
                components {
                    withModule('org.test:projectA') { ComponentMetadataDetails details ->
                        assert details.id.group == 'org.test'
                        assert details.id.name == 'projectA'
                        rulesInvoked << 1
                    }
                    withModule('org.test:projectA', new ActionRule('rulesInvoked': rulesInvoked))
                    withModule('org.test:projectA', new RuleObject('rulesInvoked': rulesInvoked))

                    withModule('org.test:projectB') { ComponentMetadataDetails details ->
                        rulesUninvoked << 1
                    }
                    withModule('org.test:projectB', new ActionRule('rulesInvoked': rulesUninvoked))
                    withModule('org.test:projectB', new RuleObject('rulesInvoked': rulesUninvoked))

                    withModule('org.test:projectA', InvokedRule)
                    withModule('org.test:projectB', NotInvokedRule)
                }
            }

            class ActionRule implements Action<ComponentMetadataDetails> {
                List rulesInvoked

                void execute(ComponentMetadataDetails details) {
                    rulesInvoked << 2
                }
            }

            class RuleObject {
                List rulesInvoked

                @org.gradle.model.Mutate
                void execute(ComponentMetadataDetails details) {
                    rulesInvoked << 3
                }
            }

            def rules1 = provider { rulesInvoked }
            def rules2 = provider { rulesUninvoked }
            resolve.doLast {
                assert rules1.get().sort() == [ 1, 2, 3 ]
                assert rules2.get().empty
                assert InvokedRule.ruleInvoked
                assert !NotInvokedRule.ruleInvoked
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
    }

    def "produces sensible error when @Mutate method does not have ComponentMetadata as first parameter"() {
        buildFile << """
            dependencies {
                components {
                    all(new BadRuleSource())
                }
            }

            class BadRuleSource {
                @org.gradle.model.Mutate
                void doSomething(String s) { }
            }
        """

        when:
        fails "resolve"

        then:
        fails 'resolveConf'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasCause("""Type BadRuleSource is not a valid rule source:
- Method doSomething(java.lang.String) is not a valid rule method: First parameter of a rule method must be of type org.gradle.api.artifacts.ComponentMetadataDetails""")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "rule that accepts IvyModuleDescriptor isn't invoked for Maven component"() {
        given:
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile <<
            """
def plainRuleInvoked = false
def ivyRuleInvoked = false
def mavenRuleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details ->
            plainRuleInvoked = true
        }
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ivyRuleInvoked = true
        }
        all { ComponentMetadataDetails details, PomModuleDescriptor descriptor ->
            mavenRuleInvoked = true
        }
    }
}

resolve.doLast {
    assert plainRuleInvoked
    assert mavenRuleInvoked
    assert !ivyRuleInvoked
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
        // also works when already cached
        succeeds 'resolve'
    }

    def 'class based rule does not get access to IvyModuleDescriptor for Maven component'() {
        given:
        repository {
            'org.test:projectA:1.0'()
        }

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectResolve()
            }
        }
        buildFile << """
class IvyRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        assert context.getDescriptor(IvyModuleDescriptor) ${GradleMetadataResolveRunner.useIvy() ? '!=' : '=='} null
    }
}

dependencies {
    components {
        all(IvyRule)
    }
}
"""

        then:
        succeeds 'resolve'
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def 'rule can access PomModuleDescriptor for Maven component'() {
        given:
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile << """
class PomRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        println(context)
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

    @Issue("gradle/gradle#4261")
    def "different projects can apply different metadata rules for the same component"() {
        repository {
            'org.test:projectA:1.0'()
            'org.test:projectB:1.0'()
        }

        settingsFile << """
rootProject.name = 'root'
include 'sub'
"""
        buildFile << """
class AddDependencyRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        context.details.allVariants {
            withDependencies {
                add('org.test:projectB:1.0')
            }
        }
    }
}

project (':sub') {
    $repositoryDeclaration

    configurations {
        conf
        other
    }
    dependencies {
        conf 'org.test:projectA:1.0'
        other 'org.test:projectA:1.0'

        // Component metadata rule that applies only to the 'sub' project
        components {
            withModule('org.test:projectA', AddDependencyRule)
        }
    }
    task res {
        def conf = configurations.conf
        def other = configurations.other
        doLast {
            // If we resolve twice the modified component metadata for 'projectA' must not be cached in-memory
            println conf.collect { it.name }
            println other.collect { it.name }
        }
    }
}

task res {
    def conf = configurations.conf
    doLast {
        // Should get the unmodified component metadata for 'projectA'
        println conf.collect { it.name }
        assert conf.collect { it.name } == ['projectA-1.0.jar']
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
        succeeds ':sub:res', ':res'
    }

    def "dependency injection works if class-based and inline rules are combined"() {
        repository {
            'org.test:projectA:1.0'()
        }

        when:
        buildFile << """
            class ClassBasedRule implements ComponentMetadataRule {
                @javax.inject.Inject
                ObjectFactory getObjects() { }
                void execute(ComponentMetadataContext context) { getObjects() }
            }
            dependencies {
                conf 'org.test:projectA:1.0'
                components {
                    all(ClassBasedRule)
                    all {}
                }
            }
        """
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'resolve'
    }

    // In theory we shouldn't allow this because it can lead to inconsistent dependency
    // resolution: it means that two strictly equivalent configurations, with the same
    // dependencies, could resolve differently in the same project, after some rules
    // have been added. However, the Nebula Resolution Rules plugin depends on this
    // behavior, because it downloads rules in a JSON format and applies them to the
    // current project.
    @Issue("https://github.com/gradle/gradle/issues/15312")
    def "rules can be added after a first resolution happened in the project"() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """

class LoggingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            println "I am executed on \${context.details.id}"
    }
}

configurations {
    ruleDownloader.incoming.afterResolve {
        println "Adding rules"
        project.dependencies {
            components {
                all(LoggingRule)
            }
        }
    }
}

dependencies {
    ruleDownloader files('rules.json')
    conf 'org.test:projectA:1.0'
}

task downloadRules {
    def files = configurations.ruleDownloader
    doFirst {
        files.files // trigger resolution before rules are added
    }
}

resolve.dependsOn(downloadRules)

"""

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'

        and:
        outputContains "I am executed on org.test:projectA:1.0"
    }
}
