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
        configure(ear)
    }

    AbstractArchiveTask getArchiveTask() {
        ear
    }

    @Test public void testEar() {
        assertEquals(Ear.EAR_EXTENSION, ear.extension)
    }

    @Test public void testLibDirName() {
        ear.libDirName = "APP-INF/lib"
        assertEquals(ear.libDirName, ear.lib.destPath as String)
    }

    @Test public void testDeploymentDescriptor() {
        ear.deploymentDescriptor = new DefaultDeploymentDescriptor(null)
        checkDeploymentDescriptor()
    }

    @Test public void testDeploymentDescriptorWithNullManifest() {
        ear.deploymentDescriptor = null
        checkDeploymentDescriptor()
    }

    public void checkDeploymentDescriptor() {
        ear.deploymentDescriptor {
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
            //TODO SF: not tested
            withXml { new Node(it, "data-source", "my/data/source") }
        }
        assertEquals("myApp.xml", ear.deploymentDescriptor.fileName)
        assertEquals("5", ear.deploymentDescriptor.version)
        assertEquals("myapp", ear.deploymentDescriptor.applicationName)
        assertEquals(true, ear.deploymentDescriptor.initializeInOrder)
        assertEquals("My App", ear.deploymentDescriptor.displayName)
        assertEquals("My Application", ear.deploymentDescriptor.description)
        assertEquals("APP-INF/lib", ear.deploymentDescriptor.libraryDirectory)
        assertEquals(2, ear.deploymentDescriptor.modules.size())
        assertEquals("my.jar", (ear.deploymentDescriptor.modules as List)[0].path)
        assertEquals("my.war", (ear.deploymentDescriptor.modules as List)[1].path)
        assertEquals("/", (ear.deploymentDescriptor.modules as List)[1].contextRoot)
        assertEquals("java", ear.deploymentDescriptor.moduleTypeMappings["my.jar"])
        assertEquals("web", ear.deploymentDescriptor.moduleTypeMappings["my.war"])
        assertEquals(2, ear.deploymentDescriptor.securityRoles.size())
        assertEquals("admin", (ear.deploymentDescriptor.securityRoles as List)[0].roleName)
        assertEquals("superadmin", (ear.deploymentDescriptor.securityRoles as List)[1].roleName)
    }
}