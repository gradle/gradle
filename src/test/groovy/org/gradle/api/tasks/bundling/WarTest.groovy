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
import org.gradle.api.DependencyManager
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.util.FileSet
import org.gradle.api.tasks.util.FileCollection
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class WarTest extends AbstractArchiveTaskTest {
    static final List TEST_LIB_CONFIGURATIONS = ['testLibConf1', 'testLibConf2'] 

    War war
    
    MockFor antWarMocker

    Map filesFromDepencencyManager

    List dependencyManagerMockCheckList

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
        dependencyManagerMockCheckList = new ArrayList(TEST_LIB_CONFIGURATIONS)
        antWarMocker = new MockFor(AntWar)
        filesFromDepencencyManager = [testLibConf1: ['/file1' as File], testLibConf2: ['/file2' as File]]
        dependencyManagerMock.demand.resolve(2..2) { String configuration ->
            dependencyManagerMockCheckList = dependencyManagerMockCheckList - configuration
            filesFromDepencencyManager[configuration]
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
                assertEquals(new ArrayList(filesFromDepencencyManager.values()).flatten(), dependencyLibFiles)
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

}