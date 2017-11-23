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

import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.test.fixtures.encoding.Identifier
import spock.lang.Unroll

class ComponentMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest {
    String getDefaultStatus() {
        GradleMetadataResolveRunner.useIvy()?'integration':'release'
    }

    def setup() {
        buildFile <<
"""
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
    }

    def "rule receives correct metadata"() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile <<
"""
dependencies {
    components {
        all { ComponentMetadataDetails details ->
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

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
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
dependencies {
    components {
        all { ComponentMetadataDetails details ->
            details.status "integration.changed" // verify that 'details' is enhanced
            details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
            details.changing = true
        }
        all { ComponentMetadataDetails details ->
            assert details.status == "integration.changed"
            assert details.statusScheme == ["integration.changed", "milestone.changed", "release.changed"]
            assert details.changing
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
    }

    def "changes made by a rule are not cached"() {
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile <<
                """
dependencies {
    components {
        all { ComponentMetadataDetails details ->
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

    def "can apply all rule types to all modules" () {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """
            ext.rulesInvoked = []
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

            resolve.doLast { assert rulesInvoked == [ '1.0', '1.0', '1.0', '1.0', '1.0' ] }
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

    def "can apply all rule types by module" () {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """
            ext.rulesInvoked = []
            ext.rulesUninvoked = []
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

            resolve.doLast {
                assert rulesInvoked.sort() == [ 1, 2, 3 ]
                assert rulesUninvoked.empty
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

    // Ivy specific tests
    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
    )
    def "can access Ivy metadata by accepting parameter of type IvyModuleDescriptor"() {
        given:
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withExtraInfo((ns('foo')): "fooValue", (ns('bar')): "barValue")
                    withBranch('someBranch')
                    withStatus('release')
                }
            }
        }

        buildFile <<
            """
def ruleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo.asMap() == [${declareNS('foo')}: "fooValue", ${declareNS('bar')}: "barValue"]
            assert descriptor.extraInfo.get('foo') == 'fooValue'
            assert descriptor.extraInfo.get('${ns('foo').namespace}', 'foo') == 'fooValue'
            assert descriptor.branch == 'someBranch'
            assert descriptor.ivyStatus == 'release'
        }
    }
}

resolve.doLast { assert ruleInvoked }
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

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
    )
    def "produces sensible error when accessing non-unique extra info element name"() {
        given:
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withExtraInfo((ns('foo')): "fooValue", (new NamespaceId('http://some.other.ns', 'foo')): "barValue")
                }
            }
        }

        buildFile <<
            """
def ruleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            descriptor.extraInfo.get('foo')
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        and:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectGetMetadata()
            }
        }

        when:
        fails 'resolve'

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasLineNumber(48)
        failure.assertHasCause("Could not resolve all files for configuration ':conf'.")
        failure.assertHasCause("Could not resolve org.test:projectA:1.0.")
        failure.assertHasCause("Cannot get extra info element named 'foo' by name since elements with this name were found from multiple namespaces (http://my.extra.info/foo, http://some.other.ns).  Use get(String namespace, String name) instead.")
    }

    @Unroll
    def "can access Ivy metadata with #identifier characters"() {
        given:
        def branch = identifier.safeForBranch().decorate("branch")
        def status = identifier.safeForFileName().decorate("status")
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withBranch(branch)
                    withStatus(status)
                }
            }
        }

        buildFile <<
            """
def ruleInvoked = false

dependencies {
    components {
        all { details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.branch == '${sq(branch)}'
            details.statusScheme = [ '${sq(status)}' ]
            assert descriptor.ivyStatus == '${sq(status)}'
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        expect:
        // Cannot use @RequiredFeatures because of Unroll above
        if (GradleMetadataResolveRunner.useIvy()) {
            repositoryInteractions {
                'org.test:projectA:1.0' {
                    expectResolve()
                }
            }
            succeeds 'resolve'
        }

        where:
        identifier << Identifier.all
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
    )
    def "rule that doesn't initially access Ivy metadata can be changed to get access at any time"() {
        given:
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withExtraInfo((ns('foo')): "fooValue", (ns('bar')): "barValue")
                    withBranch("someBranch")
                    withStatus("release")
                }
            }
        }

        def baseScript = buildFile.text

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectResolve()
            }
        }
        buildFile.text = baseScript +
            """
def ruleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details ->
            ruleInvoked = true
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        then:
        succeeds 'resolve'

        when:
        buildFile.text = baseScript +
            """
def ruleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo.asMap() == [${declareNS('foo')}: "fooValue", ${declareNS('bar')}: "barValue"]
            assert descriptor.branch == 'someBranch'
            assert descriptor.ivyStatus == 'release'
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        then:
        succeeds 'resolve'
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
    )
    def "changed Ivy metadata becomes visible once module is refreshed"() {
        def baseScript = buildFile.text

        given:
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withExtraInfo((ns('foo')): "fooValue", (ns('bar')): "barValue")
                    withBranch('someBranch')
                    withStatus('release')
                }
            }
        }

        buildFile.text = baseScript +
            """
def ruleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo.asMap() == [${declareNS('foo')}: "fooValue", ${declareNS('bar')}: "barValue"]
            assert descriptor.branch == 'someBranch'
            assert descriptor.ivyStatus == 'release'
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'resolve', '--refresh-dependencies'

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withExtraInfo((ns('foo')): "fooValueChanged", (ns('bar')): "barValueChanged")
                    withBranch('differentBranch')
                    withStatus('milestone')
                    publishWithChangedContent()
                }
            }
        }

        then:
        succeeds 'resolve'

        when:
        buildFile.text = baseScript +
            """
def ruleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            file("metadata").delete()
            file("metadata") << descriptor.extraInfo.asMap().toString()
            file("metadata") << "\\n"
            file("metadata") << descriptor.branch
            file("metadata") << "\\n"
            file("metadata") << descriptor.ivyStatus
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        and:
        resetExpectations()
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withExtraInfo((ns('foo')): "fooValueChanged", (ns('bar')): "barValueChanged")
                    withBranch('differentBranch')
                    withStatus('milestone')
                    publishWithChangedContent()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectHeadMetadata()
                withModule {
                    // todo: handle this properly in ModuleVersionSpec test fixture
                    getArtifact(name: 'ivy', ext: 'xml.sha1').allowGetOrHead()
                    if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                        getArtifact(ext: 'module.sha1').allowGetOrHead()
                    }
                }
                expectGetMetadata()
                expectHeadArtifact()
                expectGetArtifact()
                withModule {
                    getArtifact(ext: 'jar.sha1').allowGetOrHead()
                }
            }
        }

        then:
        succeeds 'resolve', '--refresh-dependencies'
        assert file("metadata").text == "{{http://my.extra.info/bar}bar=barValueChanged, {http://my.extra.info/foo}foo=fooValueChanged}\ndifferentBranch\nmilestone"
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
    )
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

    @RequiredFeatures(
        @RequiredFeature(feature=GradleMetadataResolveRunner.REPOSITORY_TYPE, value="maven")
    )
    def "rule that accepts IvyModuleDescriptor isn't invoked for Maven component"() {
        given:
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile <<
            """
def plainRuleInvoked = false
def ivyRuleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details ->
            plainRuleInvoked = true
        }
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ivyRuleInvoked = true
        }
    }
}

resolve.doLast {
    assert plainRuleInvoked
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

    def ns(String name) {
        return new NamespaceId("http://my.extra.info/${name}", name)
    }

    def declareNS(String name) {
        return "(new javax.xml.namespace.QName('http://my.extra.info/${name}', '${name}'))"
    }

    String sq(String input) {
        return escapeForSingleQuoting(input)
    }

    String escapeForSingleQuoting(String input) {
        return input.replace('\\', '\\\\').replace('\'', '\\\'')
    }
}
