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

package org.gradle.api.tasks.bundling

import org.gradle.api.enterprise.archives.internal.DefaultDeploymentDescriptor
import org.gradle.api.plugins.EarPluginConvention
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertEquals

/**
 * @author David Gileadi
 */
class EarTest extends AbstractArchiveTaskTest {

    Ear ear

    Map filesFromDepencencyManager

    @Before public void setUp() {
        super.setUp()
        ear = createTask(Ear)
        ear.earModel = new EarPluginConvention(resolver)
        configure(ear)
    }

    AbstractArchiveTask getArchiveTask() {
        ear
    }

    @Test public void testEar() {
        assertEquals(Ear.EAR_EXTENSION, ear.extension)
    }

    @Test public void testLibDirName() {
        ear.earModel.libDirName = "APP-INF/lib"
        assertEquals(ear.earModel.libDirName, ear.lib.destPath as String)
    }

    @Test public void testDeploymentDescriptor() {
        ear.earModel.deploymentDescriptor = new DefaultDeploymentDescriptor(null)
        checkDeploymentDescriptor()
    }

    @Test public void testDeploymentDescriptorWithNullManifest() {
        ear.deploymentDescriptor = null
        checkDeploymentDescriptor()
    }

    public void checkDeploymentDescriptor() {
        ear.earModel.deploymentDescriptor {
            fileName = "myApp.xml"
            version = "5"
            applicationName = "myapp"
            initializeInOrder = true
            displayName = "My App"
            description = "My Application"
            libraryDirectory = "APP-INF/lib"
            module("my.jar", "java")
            webModule("my.war", "/")
            securityRole "admin"
            securityRole "superadmin"
            withXml { provider ->
                //just adds an action
            }
        }
        def d = ear.earModel.deploymentDescriptor
        assertEquals("myApp.xml", d.fileName)
        assertEquals("5", d.version)
        assertEquals("myapp", d.applicationName)
        assertEquals(true, d.initializeInOrder)
        assertEquals("My App", d.displayName)
        assertEquals("My Application", d.description)
        assertEquals("APP-INF/lib", d.libraryDirectory)
        assertEquals(2, d.modules.size())
        assertEquals("my.jar", (d.modules as List)[0].path)
        assertEquals("my.war", (d.modules as List)[1].path)
        assertEquals("/", (d.modules as List)[1].contextRoot)
        assertEquals("java", d.moduleTypeMappings["my.jar"])
        assertEquals("web", d.moduleTypeMappings["my.war"])
        assertEquals(2, d.securityRoles.size())
        assertEquals("admin", (d.securityRoles as List)[0].roleName)
        assertEquals("superadmin", (d.securityRoles as List)[1].roleName)
        assertEquals(1, d.transformer.actions.size())
    }
}