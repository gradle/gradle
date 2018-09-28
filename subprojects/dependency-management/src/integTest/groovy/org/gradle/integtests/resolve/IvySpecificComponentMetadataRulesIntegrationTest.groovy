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

package org.gradle.integtests.resolve

import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.encoding.Identifier
import spock.lang.Unroll

@RequiredFeatures([
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy"),
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false"),
])
class IvySpecificComponentMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest implements ComponentMetadataRulesSupport {

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
        new ResolveTestFixture(buildFile).addDefaultVariantDerivationStrategy()
    }

    def "can access Ivy metadata"() {
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
class IvyRule implements ComponentMetadataRule {
    static boolean ruleInvoked

    @Override
    void execute(ComponentMetadataContext context) {
            ruleInvoked = true
            def descriptor = context.getDescriptor(IvyModuleDescriptor)
            assert descriptor.extraInfo.asMap() == [${declareNS('foo')}: "fooValue", ${declareNS('bar')}: "barValue"]
            assert descriptor.extraInfo.get('foo') == 'fooValue'
            assert descriptor.extraInfo.get('${ns('foo').namespace}', 'foo') == 'fooValue'
            assert descriptor.branch == 'someBranch'
            assert descriptor.ivyStatus == 'release'
    }
}

dependencies {
    components {
        all(IvyRule)
    }
}

resolve.doLast { assert IvyRule.ruleInvoked }
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

    def "produces sensible error when accessing non-unique extra info element name"() {
        given:
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withExtraInfo((ns('foo')): "fooValue", (new NamespaceId('http://some.other.ns', 'foo')): "barValue")
                }
            }
        }

        buildFile << """
class IvyRule implements ComponentMetadataRule {

    @Override
    void execute(ComponentMetadataContext context) {
            def descriptor = context.getDescriptor(IvyModuleDescriptor)
            descriptor.extraInfo.get('foo')
    }
}

dependencies {
    components {
        all(IvyRule)
    }
}
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
        failure.assertHasLineNumber(51)
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
class IvyRule implements ComponentMetadataRule {
    static boolean ruleInvoked

    @Override
    void execute(ComponentMetadataContext context) {
            ruleInvoked = true
            def descriptor = context.getDescriptor(IvyModuleDescriptor)
            assert descriptor.branch == '${sq(branch)}'
            context.details.statusScheme = [ '${sq(status)}' ]
            assert descriptor.ivyStatus == '${sq(status)}'
    }
}

dependencies {
    components {
        all(IvyRule)
    }
}

resolve.doLast { assert IvyRule.ruleInvoked }
"""

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'resolve'

        where:
        identifier << Identifier.all
    }

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

}
