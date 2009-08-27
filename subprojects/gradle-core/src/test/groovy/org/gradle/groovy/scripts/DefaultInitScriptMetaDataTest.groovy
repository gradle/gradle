/*
 * Copyright 2007-2008 the original author or authors.
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
import static org.junit.Assert.assertEquals
import org.gradle.util.JUnit4GroovyMockery
import org.junit.Test
import org.gradle.api.invocation.Gradle

class DefaultInitScriptMetaDataTest {
    @Test public void testApplyMetaData() {
        JUnit4GroovyMockery context = new JUnit4GroovyMockery()

        DefaultInitScriptMetaData initScriptMetaData = new DefaultInitScriptMetaData()
        Gradle gradleMock = context.mock(Gradle)
        Script script = new GroovyShell(createBaseCompilerConfiguration()).parse(testScriptText)
        initScriptMetaData.applyMetaData(script, gradleMock)

        context.checking {
            one(gradleMock).getRootProject();
        }

        script.run();
        assertEquals("scriptMethod", script.scriptMethod())
    }

    private CompilerConfiguration createBaseCompilerConfiguration() {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.scriptBaseClass = ScriptWithSource.class.name
        configuration
    }

    private String getTestScriptText() {
        '''
getRootProject() // call a project method
def scriptMethod() { 'scriptMethod' }
String internalProp = 'a'
assert internalProp == 'a'
'''
    }
}
