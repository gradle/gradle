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

import org.gradle.api.artifacts.NamespaceId
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
        eachComponent { details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo == [${declareNS('foo')}: "fooValue", ${declareNS('bar')}: "barValue"]
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
        eachComponent { details, IvyModuleDescriptor descriptor ->
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
        eachComponent { details ->
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
        eachComponent { details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo == [${declareNS('foo')}: "fooValue", ${declareNS('bar')}: "barValue"]
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
        eachComponent { details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo == [${declareNS('foo')}: "fooValue", ${declareNS('bar')}: "barValue"]
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
        eachComponent { details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            file("metadata").delete()
            file("metadata") << descriptor.extraInfo.toString()
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
        assert file("metadata").text == "{bar=barValueChanged, foo=fooValueChanged}\ndifferentBranch\nmilestone"
    }

    def ns(String name) {
        return new NamespaceId("http://my.extra.info/${name}", name)
    }

    def declareNS(String name) {
        return "(new NamespaceId('http://my.extra.info/${name}', '${name}'))"
    }
}
