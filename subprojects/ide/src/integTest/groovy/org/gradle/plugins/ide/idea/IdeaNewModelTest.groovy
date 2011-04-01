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

class IdeaNewModelTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void enablesCustomizationsOnNewModel() {
        //given
        testResources.dir.create {
            additionalCustomSources {}
            src { main { java {} } }
        }

        //when
        runTask 'idea', '''
apply plugin: "java"
apply plugin: "idea"

configurations {
  provided
  provided.extendsFrom(compile)
}

dependencies { provided "junit:junit:4.8.2" }

idea {
    module {
        sourceDirs += file('additionalCustomSources')
        name = 'foo'
        scopes.PROVIDED.plus += configurations.provided
        downloadJavadoc = true
        downloadSources = false
    }
}
'''

        //then
        def iml = parseImlFile('foo') //println getFile([:], 'root.iml').text
        ['additionalCustomSources', 'src/main/java'].each { expectedSrcFolder ->
            assert iml.component.content.sourceFolder.find { it.@url.text().contains(expectedSrcFolder) }
        }
    }

    private parseImlFile(Map options = [:], String projectName) {
        parseFile(options, "${projectName}.iml")
    }
}
