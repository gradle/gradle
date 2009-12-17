/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.internal.project.DefaultProject
import static org.junit.Assert.assertEquals
import org.junit.Test
import org.gradle.api.internal.project.ServiceRegistry
import org.junit.runner.RunWith
import org.jmock.integration.junit4.JMock
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.api.initialization.dsl.ScriptHandler

/**
 * @author Hans Dockter
 */
@RunWith(JMock)
class DefaultScriptTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Test public void testApplyMetaData() {
        ServiceRegistry serviceRegistryMock = context.mock(ServiceRegistry.class)
        context.checking {
            allowing(serviceRegistryMock).get(ScriptHandler.class)
            will(returnValue(context.mock(ScriptHandler.class)))
        }

        DefaultScript script = new GroovyShell(createBaseCompilerConfiguration()).parse(testScriptText)
        DefaultProject testProject = new DefaultProject('someproject')
        testProject.custom = 'true'
        script.setScriptSource(new StringScriptSource('script', '//'))
        script.init(testProject, serviceRegistryMock)
        script.run();
        assertEquals("scriptMethod", script.scriptMethod())
        assertEquals(testProject.path + "mySuffix", script.scriptProperty)
        assertEquals(testProject.path + "mySuffix", testProject.additionalProperties["scriptProperty"])
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
def scriptMethod() { 'scriptMethod' }
scriptProperty = project.path + 'mySuffix'
String internalProp = 'a'
assert internalProp == 'a'
newProperty = 'a'
assert newProperty == 'a'
assert newProperty == project.newProperty
'''
    }
}
