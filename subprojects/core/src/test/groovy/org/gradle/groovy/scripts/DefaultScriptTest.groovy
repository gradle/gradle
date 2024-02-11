/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.groovy.scripts

import org.codehaus.groovy.control.CompilerConfiguration
import org.gradle.api.internal.project.DefaultDynamicLookupRoutine
import org.gradle.api.internal.project.DynamicLookupRoutine
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.service.ServiceRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultScriptTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    def testApplyMetaData() {
        ServiceRegistry serviceRegistryMock = Mock(ServiceRegistry) {
            get(DynamicLookupRoutine) >> new DefaultDynamicLookupRoutine()
        }

        when:
        DefaultScript script = new GroovyShell(createBaseCompilerConfiguration()).parse(testScriptText)
        ProjectInternal testProject = TestUtil.create(temporaryFolder).rootProject()
        testProject.ext.custom = 'true'
        script.setScriptSource(new TextResourceScriptSource(new StringTextResource('script', '//')))
        script.init(testProject, serviceRegistryMock)
        script.run();

        then:
        script.scriptMethod() == "scriptMethod"
        script.newProperty == "a"
    }

    private CompilerConfiguration createBaseCompilerConfiguration() {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.scriptBaseClass = DefaultScript.class.name
        configuration
    }

    private String getTestScriptText() {
        '''
// We leave out the path to check import adding
getName() // call a project method
assert hasProperty('custom')
repositories { }
def scriptMethod() { 'scriptMethod' }
String internalProp = 'a'
assert internalProp == 'a'
ext.newProperty = 'a'
assert newProperty == 'a'
assert newProperty == project.newProperty
'''
    }
}
