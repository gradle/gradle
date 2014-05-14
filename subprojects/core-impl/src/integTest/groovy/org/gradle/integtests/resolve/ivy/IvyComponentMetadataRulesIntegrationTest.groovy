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
import org.gradle.test.fixtures.server.http.IvyHttpRepository

import static org.gradle.util.Matchers.containsLine

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

    def "can access Ivy extra info by accepting parameter of type IvyModuleDescriptor"() {
        repo.module('org.test', 'projectA', '1.0').withExtraInfo(foo: "fooValue", bar: "barValue").publish().allowAll()

        buildFile <<
"""
def ruleInvoked = false

dependencies {
    components {
        eachComponent { details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo == ["my:foo": "fooValue", "my:bar": "barValue"]
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

    def "rule that doesn't initially access Ivy extra info can be changed to get access at any time"() {
        repo.module('org.test', 'projectA', '1.0').withExtraInfo(foo: "fooValue", bar: "barValue").publish().allowAll()
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
            assert descriptor.extraInfo == ["my:foo": "fooValue", "my:bar": "barValue"]
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        then:
        succeeds 'resolve'
    }

    def "changed Ivy extra info becomes visible once module is refreshed"() {
        def baseScript = buildFile.text

        when:
        repo.module('org.test', 'projectA', '1.0').withExtraInfo(foo: "fooValue", bar: "barValue").publish().allowAll()
        buildFile.text = baseScript +
                """
def ruleInvoked = false

dependencies {
    components {
        eachComponent { details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            assert descriptor.extraInfo == ["my:foo": "fooValue", "my:bar": "barValue"]
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        then:
        succeeds 'resolve'

        when:
        repo.module('org.test', 'projectA', '1.0').withExtraInfo(foo: "fooValueChanged", bar: "barValueChanged").publishWithChangedContent()

        then:
        succeeds 'resolve'

        when:
        repo.module('org.test', 'projectA', '1.0').withExtraInfo(foo: "fooValueChanged", bar: "barValueChanged").publishWithChangedContent()
        args("--refresh-dependencies")
        buildFile.text = baseScript +
"""
def ruleInvoked = false

dependencies {
    components {
        eachComponent { details, IvyModuleDescriptor descriptor ->
            ruleInvoked = true
            file("extraInfo").delete()
            descriptor.extraInfo.each { key, value ->
                file("extraInfo") << "\$key->\$value\\n"
            }
        }
    }
}

resolve.doLast { assert ruleInvoked }
"""

        then:
        succeeds 'resolve'
        // TODO: rule is invoked twice, and changes to extra info are only visible the second time
        def text = file("extraInfo").text
        assert containsLine(text, "my:foo->fooValueChanged")
        assert containsLine(text, "my:bar->barValueChanged")
    }
}
