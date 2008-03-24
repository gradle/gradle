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
 
package org.gradle.api.internal.project

import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
class EmbeddedBuildScriptFinderTest extends GroovyTestCase {
    EmbeddedBuildScriptFinder buildScriptFinder

    DefaultProject rootProject

    String expectedEmbeddedBuildScript

    void setUp() {
        expectedEmbeddedBuildScript = 'somescript'
        buildScriptFinder = new EmbeddedBuildScriptFinder(expectedEmbeddedBuildScript)
    }

    void testGetBuildScript() {
        DefaultProject rootProject = HelperUtil.createRootProject(new File('root'))
        DefaultProject childProject = rootProject.addChildProject('name')
        assertEquals(expectedEmbeddedBuildScript, buildScriptFinder.getBuildScript(rootProject))
        assertEquals(expectedEmbeddedBuildScript, buildScriptFinder.getBuildScript(childProject))
    }

    void testGetBuildFileName() {
        assertEquals(EmbeddedBuildScriptFinder.EMBEDDED_SCRIPT_NAME, buildScriptFinder.buildFileName)    
    }
}
