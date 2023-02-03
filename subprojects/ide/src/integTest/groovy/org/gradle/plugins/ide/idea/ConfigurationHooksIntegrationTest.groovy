/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.TestResources
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Rule
import org.junit.Test

class ConfigurationHooksIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Test
    @ToBeFixedForConfigurationCache
    void triggersBeforeAndWhenConfigurationHooks() {

        //this test is a bit peculiar as it has assertions inside the gradle script
        //couldn't find a better way of asserting on before/when configured hooks
        runIdeaTask '''
apply plugin: 'java'
apply plugin: 'idea'

def beforeConfiguredObjects = 0
def whenConfiguredObjects = 0

idea {
    project {
        ipr {
            beforeMerged {beforeConfiguredObjects++ }
            whenMerged {whenConfiguredObjects++ }
        }
    }
    workspace {
        iws {
            beforeMerged {beforeConfiguredObjects++ }
            whenMerged {whenConfiguredObjects++ }
        }
    }
    module {
        iml {
            beforeMerged {beforeConfiguredObjects++ }
            whenMerged {whenConfiguredObjects++ }
        }
    }
}

tasks.idea {
    doLast {
        assert beforeConfiguredObjects == 3 : "beforeConfigured() hooks should be fired for domain model objects"
        assert whenConfiguredObjects == 3 : "whenConfigured() hooks should be fired for domain model objects"
    }
}
'''
    }

    @Test
    @ToBeFixedForConfigurationCache
    void whenHooksApplyChangesToGeneratedFile() {
        //when
        runIdeaTask '''
apply plugin: 'java'
apply plugin: 'idea'

idea {
    module {
        iml {
            whenMerged { it.jdkName = '1.44' }
        }
    }

    project {
        ipr {
            whenMerged { it.wildcards += '!?*.ruby' }
        }
    }
}
'''
        //then
        def iml = getFile([:], 'root.iml').text
        assert iml.contains('1.44')

        def ipr = getFile([:], 'root.ipr').text
        assert ipr.contains('!?*.ruby')
    }


    private containsDir(path, urls) {
        urls.any { it.endsWith(path) }
    }
}
