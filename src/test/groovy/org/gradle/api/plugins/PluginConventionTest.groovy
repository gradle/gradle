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
 
package org.gradle.api.plugins

import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
abstract class AbstractPluginConventionTest extends GroovyTestCase {
    File testDir
    DefaultProject project

    abstract Class getType()

    abstract Map getCustomValues()

    void setUp() {
        testDir = HelperUtil.makeNewTestDir()
        project = new DefaultProject()
        project = [getProjectDir: {testDir}] as DefaultProject
    }

    void testCustomValues() {
        project.convention.plugins.test1 = new TestPluginConvention1()
        project.convention.plugins.test2 = new TestPluginConvention2()
        Map testCustomValues = getCustomValues()
        println testCustomValues
        testCustomValues.test1 = [:]
        testCustomValues.test1.a = 'newA'
        testCustomValues.c = 'newC'
        def pluginConvention = type.newInstance(project, testCustomValues)
        project.convention.plugins.newConvention = pluginConvention
        assertEquals('newA', project.convention.plugins.test1.a)
        assertEquals('newC', project.convention.plugins.test1.c)
        customValues.each { String key, value ->
            assertEquals(value, project.convention."$key")
        }
    }


}
