/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class GradleScriptExceptionTest extends GroovyTestCase {
    void testMessage() {
        String scriptName = 'scriptfile'
        GradleScriptException buildScriptException
        try {
            new GroovyShell().evaluate('''
def a = 1
def b = 2
unknownProp''', scriptName)
            fail()
        } catch (MissingPropertyException e) {
            buildScriptException = new GradleScriptException(e, 'name')
        }
        assert buildScriptException.cause instanceof MissingPropertyException
        assert buildScriptException.message.contains(MissingPropertyException.name)
    }

    
}
