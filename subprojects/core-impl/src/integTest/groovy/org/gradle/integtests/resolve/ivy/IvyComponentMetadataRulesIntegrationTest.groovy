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

    def "can access Ivy metadata by accepting parameter of type IvyModuleMetadata"() {
        def module = repo.module('org.test', 'projectA', '1.0')
                .withExtraInfo(foo: "fooValue", bar: "barValue")
                .withBranch('someBranch')
                .publish()
        module.ivy.expectDownload()
        module.artifact.expectDownload()

        buildFile <<
"""
def ruleInvoked = false

dependencies {
    components {
        eachComponent { details, IvyModuleMetadata descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo == ["my:foo": "fooValue", "my:bar": "barValue"]
            assert descriptor.branch == 'someBranch'
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
        def module = repo.module('org.test', 'projectA', '1.0')
                .withBranch(branch)
                .publish()
        module.ivy.expectDownload()
        module.artifact.expectDownload()

        buildFile <<
                """
def ruleInvoked = false

dependencies {
    components {
        eachComponent { details, IvyModuleMetadata descriptor ->
            ruleInvoked = true
            assert descriptor.branch == '${sq(branch)}'
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
                .withExtraInfo(foo: "fooValue", bar: "barValue")
                .withBranch("someBranch")
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
        eachComponent { details, IvyModuleMetadata descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo == ["my:foo": "fooValue", "my:bar": "barValue"]
            assert descriptor.branch == 'someBranch'
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
                .withExtraInfo(foo: "fooValue", bar: "barValue")
                .withBranch('someBranch')
                .publish()
        module.ivy.expectDownload()
        module.artifact.expectDownload()

        buildFile.text = baseScript +
                """
def ruleInvoked = false

dependencies {
    components {
        eachComponent { details, IvyModuleMetadata descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo == ["my:foo": "fooValue", "my:bar": "barValue"]
            assert descriptor.branch == 'someBranch'
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        then:
        succeeds 'resolve'

        when:
        repo.module('org.test', 'projectA', '1.0')
                .withExtraInfo(foo: "fooValueChanged", bar: "barValueChanged")
                .withBranch('differentBranch')
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
        eachComponent { details, IvyModuleMetadata descriptor ->
            ruleInvoked = true
            file("metadata").delete()
            file("metadata") << descriptor.extraInfo.toString()
            file("metadata") << "\\n"
            file("metadata") << descriptor.branch
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
        assert file("metadata").text == "{my:bar=barValueChanged, my:foo=fooValueChanged}\ndifferentBranch"
    }
}
