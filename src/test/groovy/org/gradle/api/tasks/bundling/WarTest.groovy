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
 
package org.gradle.api.tasks.bundling

import groovy.mock.interceptor.MockFor
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.util.FileSet
import static org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * @author Hans Dockter
 */
class WarTest extends AbstractArchiveTaskTest {
    static final List TEST_LIB_CONFIGURATIONS = ['testLibConf1', 'testLibConf2'] 
    static final List TEST_LIB_EXCLUDE_CONFIGURATIONS = ['testLibConf3', 'testLibConf4']

    War war
    
    MockFor antWarMocker

    Map filesFromDepencencyManager

    @Before public void setUp()  {
        super.setUp()
        war = new War(project, AbstractTaskTest.TEST_TASK_NAME, getTasksGraph())
        configure(war)
        war.manifest = new GradleManifest()
        war.metaInfResourceCollections = [new FileSet()]
        war.webInfFileSets = [new FileSet()]
        war.webXml = 'myweb.xml' as File
        war.classesFileSets = [new FileSet()]
        war.additionalLibFileSets = [new FileSet()]
        war.libConfigurations = TEST_LIB_CONFIGURATIONS
        war.libExcludeConfigurations = TEST_LIB_EXCLUDE_CONFIGURATIONS
        antWarMocker = new MockFor(AntWar)
        filesFromDepencencyManager = [
                testLibConf1: ['/file1' as File, '/file3' as File],
                testLibConf2: ['/file2' as File, '/file4' as File], 
                testLibConf3: ['/file3' as File],
                testLibConf4: ['/file4' as File]
        ]
        getContext().checking {
            allowing(archiveTask.dependencyManager).resolve(TEST_LIB_CONFIGURATIONS[0]); will(returnValue(filesFromDepencencyManager[TEST_LIB_CONFIGURATIONS[0]]))
            allowing(archiveTask.dependencyManager).resolve(TEST_LIB_CONFIGURATIONS[1]); will(returnValue(filesFromDepencencyManager[TEST_LIB_CONFIGURATIONS[1]]))
            allowing(archiveTask.dependencyManager).resolve(TEST_LIB_EXCLUDE_CONFIGURATIONS[0]); will(returnValue(filesFromDepencencyManager[TEST_LIB_EXCLUDE_CONFIGURATIONS[0]]))
            allowing(archiveTask.dependencyManager).resolve(TEST_LIB_EXCLUDE_CONFIGURATIONS[1]); will(returnValue(filesFromDepencencyManager[TEST_LIB_EXCLUDE_CONFIGURATIONS[1]]))
        }
    }

    AbstractArchiveTask getArchiveTask() {
        war
    }

    MockFor getAntMocker(boolean toBeCalled) {
        antWarMocker.demand.execute(toBeCalled ? 1..1 : 0..0) {AntMetaArchiveParameter metaArchiveParameter,
                                                                     List classesFileSets, List dependencyLibFiles, List additionalLibFileSets,
                                                                     List webInfFileSets, File webXml ->
            if (toBeCalled) {
                checkMetaArchiveParameterEqualsArchive(metaArchiveParameter, war)
                assert classesFileSets.is(war.classesFileSets)
                assert additionalLibFileSets.is(war.additionalLibFileSets)
                assertEquals(['/file1' as File, '/file2' as File], dependencyLibFiles)
                assertEquals(webXml, war.webXml)
                assert webInfFileSets.is(war.webInfFileSets)
            }
        }
        antWarMocker
    }

    def getAnt() {
        war.antWar
    }

    @Test public void testWar() {
        assertEquals(War.WAR_EXTENSION, war.extension)
    }

    @Test public void testLibConfigurations() {
        war.libConfigurations = null
        war.libConfigurations('a')
        assertEquals(['a'], war.libConfigurations)
        war.libConfigurations('b', 'c')
        assertEquals(['a', 'b', 'c'], war.libConfigurations)
    }

    @Test public void testLibExcludeConfigurations() {
        war.libExcludeConfigurations = null
        war.libExcludeConfigurations('a')
        assertEquals(['a'], war.libExcludeConfigurations)
        war.libExcludeConfigurations('b', 'c')
        assertEquals(['a', 'b', 'c'], war.libExcludeConfigurations)
    }

    @Test public void testWebInfFileSet() {
        checkAddFileSet("webInf", "webInfFileSets")
    }

    @Test public void testClassesFileSet() {
        checkAddFileSet("classes", "classesFileSets")
    }

    @Test public void testAditionalLibFileSet() {
        checkAddFileSet("additionalLibs", "additionalLibFileSets")
    }

    private void checkAddFileSet(String methodName, String propertyName) {
        war."$propertyName" = null
        war."$methodName"(dir: 'x') {
            include 'a'
        }
        war."$methodName"(dir: 'y')
        assertEquals(new File('x'), war."$propertyName"[0].dir)
        assertEquals(['a'] as Set, war."$propertyName"[0].includes)
        assertEquals(new File('y'), war."$propertyName"[1].dir)
    }

}