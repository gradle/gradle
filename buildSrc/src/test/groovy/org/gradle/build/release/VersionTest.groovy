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

package org.gradle.build.release

import groovy.mock.interceptor.StubFor
import org.gradle.api.Project
import org.gradle.util.GradleUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class VersionTest {
    Version version
    boolean trunk
    Project project
    Svn svn
    File testDir

    @Before
    void setUp() {
        testDir = GradleUtil.makeNewDir(new File('tmpTest'))
        File rootDir = new File(testDir, 'root')
        rootDir.mkdir()
        project = createRootProject(rootDir);
        project.previousMajor = '1'
        project.previousMinor = '2'
        project.previousRevision = '3'
        svn = [isTrunk: {trunk}] as Svn
        version = new Version(svn, project, false)
    }

    Project createRootProject(File rootDir) {
        Map props = [:]
        StubFor stub = new StubFor(Project)
        ['previousMajor', 'previousMinor', 'previousRevision', 'versionModifier'].each { String prop ->
            String capName = prop[0].toUpperCase() + prop.substring(1)
            stub.demand."set$capName"(1..20) { value -> props[prop] = value }
            stub.demand."get$capName"(1..20) { props[prop] }
        }
        stub.demand.hasProperty(1..20) {value -> props.containsKey(value) }
        stub.demand.getProjectDir(1..20) { rootDir }
        stub.proxyInstance()
    }

    @After
    void tearDown() {
        GradleUtil.deleteDir(testDir)
    }

    @Test
    void testFalseTrunkMinor() {
        trunk = true
        assertEquals(1, version.major)
        assertEquals(3, version.minor)
        assertEquals(0, version.revision)
    }

    @Test
    void testTrueTrunkMinor() {
        trunk = false
        assertEquals(1, version.major)
        assertEquals(2, version.minor)
        assertEquals(4, version.revision)
    }

    @Test
    void testFalseTrunkMajor() {
        version = new Version(svn, project, true)
        trunk = true
        assertEquals(2, version.major)
        assertEquals(0, version.minor)
        assertEquals(0, version.revision)
    }

    @Test
    void testTrueTrunkMajor() {
        version = new Version(svn, project, true)
        trunk = false
        assertEquals(1, version.major)
        assertEquals(2, version.minor)
        assertEquals(4, version.revision)
    }

    @Test
    void testGetLastRelease() {
        assertEquals('1.2.3', version.lastRelease)
        project.previousRevision = 0
        version = new Version(svn, project, false)
        assertEquals('1.2', version.lastRelease)
    }

    @Test
    void testToString() {
        trunk = false
        project.versionModifier = ''
        assertEquals("1.2.4", version.toString())
        project.versionModifier = 'a'
        assertEquals("1.2.4-a", version.toString())

        trunk = true
        project.versionModifier = ''
        assertEquals("1.3", version.toString())
        project.versionModifier = 'a'
        assertEquals("1.3-a", version.toString())
    }

    @Test
    void testStoreCurrentVersion() {
        trunk = false
        version.storeCurrentVersion()
        Properties properties = new Properties()
        FileInputStream propertiesInputStream = new FileInputStream(new File(project.projectDir, 'gradle.properties'))
        properties.load(propertiesInputStream)
        propertiesInputStream.close()
        assertEquals('1', properties.previousMajor)
        assertEquals('2', properties.previousMinor)
        assertEquals('4', properties.previousRevision)
    }
}

abstract class ProjectImpl implements Project {
    def propertyMissing(String name) {
    }

    def void setProperty(String name, value) {
    }
}