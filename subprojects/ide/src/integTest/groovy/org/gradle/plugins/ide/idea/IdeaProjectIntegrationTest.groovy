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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Rule
import org.junit.Test

class IdeaProjectIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void enablesCustomizationsOnNewModel() {
        //when
        runTask 'idea', 'include "someProjectThatWillBeExcluded", "api"', '''
allprojects {
    apply plugin: "java"
    apply plugin: "idea"
}

idea {
    project {
        javaVersion = '1.44'
        wildcards += '!?*.ruby'

        //let's remove one of the subprojects from generation:
        subprojects -= project(':someProjectThatWillBeExcluded')

        outputFile = new File(outputFile.parentFile, 'someBetterName.ipr')

        ipr {
            withXml {
                def node = it.asNode()
                node.appendNode('someInterestingConfiguration', 'hey buddy!')
            }
        }
    }
}
'''

        //then
        def ipr = getFile([:], 'someBetterName.ipr').text
        println ipr
        assert ipr.contains('1.44')
        assert ipr.contains('!?*.ruby')
        assert !ipr.contains('someProjectThatWillBeExcluded')
        assert ipr.contains('hey buddy!')
    }
}
