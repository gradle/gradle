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

import org.gradle.groovy.scripts.DefaultSettingsScriptMetaData
import org.gradle.initialization.DefaultSettings
import org.junit.Test
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class DefaultSettingsScriptMetaDataTest {
    @Test public void testApplyMetaData() {
        boolean settingsMethodCalled = false;
        DefaultSettingsScriptMetaData settingsScriptMetaData = new DefaultSettingsScriptMetaData()
        DefaultSettings testSettings = [
                someSettingsMethod: {settingsMethodCalled = true},
                someOtherSettingsMethod: { 'testvalue' }
        ] as DefaultSettings
        Script script = new GroovyShell().parse(testScriptText)
        settingsScriptMetaData.applyMetaData(script, testSettings)
        script.run();
        assertEquals("scriptMethod", script.scriptMethod())
        assertEquals(testSettings.someOtherSettingsMethod() + "mySuffix", script.scriptProperty)
    }

    private String getTestScriptText() {
        '''
// We leave out the path to check import adding
someSettingsMethod()
def scriptMethod() { 'scriptMethod' }
scriptProperty = someOtherSettingsMethod() + 'mySuffix'
String internalProp = 'a'
assert internalProp == 'a'
newProperty = 'a'
assert newProperty == 'a'
'''
    }
}
