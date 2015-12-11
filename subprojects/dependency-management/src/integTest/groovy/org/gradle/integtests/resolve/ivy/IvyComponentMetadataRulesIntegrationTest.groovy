/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.resolve.ivy

import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.integtests.resolve.ComponentMetadataRulesIntegrationTest
import org.gradle.test.fixtures.encoding.Identifier
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import spock.lang.Unroll

class IvyComponentMetadataRulesIntegrationTest extends ComponentMetadataRulesIntegrationTest {
    @Override
    IvyHttpRepository getRepo() {
        ivyHttpRepo
    }

    @Override
    String getRepoDeclaration() {
"""
repositories {
    ivy {
        url "$ivyHttpRepo.uri"
    }
}
"""
    }

    @Override
    String getDefaultStatus() {
        "integration"
    }

    def "can access Ivy metadata by accepting parameter of type IvyModuleDescriptor"() {
        def module = repo.module('org.test', 'projectA', '1.0')
                .withExtraInfo((ns('foo')): "fooValue", (ns('bar')): "barValue")
                .withBranch('someBranch')
                .withStatus('release')
                .publish()
        module.ivy.expectDownload()
        module.artifact.expectDownload()

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

        expect:
        succeeds 'resolve'
        // also works when already cached
        succeeds 'resolve'
    }

    def "produces sensible error when accessing non-unique extra info element name" () {
        def module = repo.module('org.test', 'projectA', '1.0')
                .withExtraInfo((ns('foo')): "fooValue", (new NamespaceId('http://some.other.ns', 'foo')): "barValue")
                .publish()
        module.ivy.expectDownload()

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

        when:
        fails 'resolve'

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasLineNumber(33)
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Cannot get extra info element named 'foo' by name since elements with this name were found from multiple namespaces (http://my.extra.info/foo, http://some.other.ns).  Use get(String namespace, String name) instead.")
    }

    @Unroll
    def "can access Ivy metadata with #identifier characters" () {
        def branch = identifier.safeForBranch().decorate("branch")
        def status = identifier.safeForFileName().decorate("status")
        def module = repo.module('org.test', 'projectA', '1.0')
                .withBranch(branch)
                .withStatus(status)
                .publish()
        module.ivy.expectDownload()
        module.artifact.expectDownload()

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
        succeeds 'resolve'

        where:
        identifier << Identifier.all
    }

    def "rule that doesn't initially access Ivy metadata can be changed to get access at any time"() {
        def module = repo.module('org.test', 'projectA', '1.0')
                .withExtraInfo((ns('foo')): "fooValue", (ns('bar')): "barValue")
                .withBranch("someBranch")
                .withStatus("release")
                .publish()
        module.ivy.expectDownload()
        module.artifact.expectDownload()

        def baseScript = buildFile.text

        when:
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

        when:
        def module = repo.module('org.test', 'projectA', '1.0')
                .withExtraInfo((ns('foo')): "fooValue", (ns('bar')): "barValue")
                .withBranch('someBranch')
                .withStatus('release')
                .publish()
        module.ivy.expectDownload()
        module.artifact.expectDownload()

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

        when:
        repo.module('org.test', 'projectA', '1.0')
                .withExtraInfo((ns('foo')): "fooValueChanged", (ns('bar')): "barValueChanged")
                .withBranch('differentBranch')
                .withStatus('milestone')
                .publishWithChangedContent()

        and:
        server.resetExpectations()

        then:
        succeeds 'resolve'

        when:
        args("--refresh-dependencies")
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
        server.resetExpectations()
        module.ivy.expectMetadataRetrieve()
        module.ivy.sha1.expectGet()
        module.ivy.expectDownload()
        module.artifact.expectMetadataRetrieve()
        module.artifact.sha1.expectGet()
        module.artifact.expectDownload()

        then:
        succeeds 'resolve'
        assert file("metadata").text == "{{http://my.extra.info/bar}bar=barValueChanged, {http://my.extra.info/foo}foo=fooValueChanged}\ndifferentBranch\nmilestone"
    }

    def "produces sensible error when @Mutate method does not have ComponentMetadata as first parameter" () {
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

    def ns(String name) {
        return new NamespaceId("http://my.extra.info/${name}", name)
    }

    def declareNS(String name) {
        return "(new javax.xml.namespace.QName('http://my.extra.info/${name}', '${name}'))"
    }
}
