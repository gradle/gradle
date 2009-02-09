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
import org.gradle.api.GradleScriptException
import org.gradle.api.dependencies.ConfigurationResolver
import org.gradle.api.dependencies.ResolveInstruction
import org.gradle.api.dependencies.specs.DependencyTypeSpec
import org.gradle.api.dependencies.specs.Type
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.util.FileSet
import org.gradle.util.ConfigureUtil
import org.gradle.util.JUnit4GroovyMockery
import static org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
class WarTest extends AbstractArchiveTaskTest {
    static final List TEST_LIB_CONFIGURATIONS = ['testLibConf1', 'testLibConf2']
    static final List TEST_LIB_EXCLUDE_CONFIGURATIONS = ['testLibConf3', 'testLibConf4']

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    DependencyManager dependencyManager = context.mock(DependencyManager)
  
    War war

    MockFor antWarMocker

    Map filesFromDepencencyManager

    @Before public void setUp() {
        super.setUp()
        getProject().setDependencies(dependencyManager)
        war = new War(project, AbstractTaskTest.TEST_TASK_NAME)
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
    }

    private void prepareDependencyManagerMock(boolean failForMissingDependency, boolean includeProjectDependencies) {
        context.checking {
            allowing(dependencyManager).configuration(TEST_LIB_CONFIGURATIONS[0]); will(returnValue(
                    createConfigurationMock(failForMissingDependency, includeProjectDependencies, filesFromDepencencyManager[TEST_LIB_CONFIGURATIONS[0]])
            ))
            allowing(dependencyManager).configuration(TEST_LIB_CONFIGURATIONS[1]); will(returnValue(
                    createConfigurationMock(failForMissingDependency, includeProjectDependencies, filesFromDepencencyManager[TEST_LIB_CONFIGURATIONS[1]])
            ))
            allowing(dependencyManager).configuration(TEST_LIB_EXCLUDE_CONFIGURATIONS[0]); will(returnValue(
                    createConfigurationMock(failForMissingDependency, includeProjectDependencies, filesFromDepencencyManager[TEST_LIB_EXCLUDE_CONFIGURATIONS[0]])
            ))
            allowing(dependencyManager).configuration(TEST_LIB_EXCLUDE_CONFIGURATIONS[1]); will(returnValue(
                    createConfigurationMock(failForMissingDependency, includeProjectDependencies, filesFromDepencencyManager[TEST_LIB_EXCLUDE_CONFIGURATIONS[1]])
            ))
        }
    }

    private ConfigurationResolver createConfigurationMock(boolean failForMissingDependency, boolean includeProjectDependencies, List returnValue) {
        [resolve: { Closure modifier ->
            ResolveInstruction resolveInstruction = ConfigureUtil.configure(modifier, new ResolveInstruction())
            assertEquals(failForMissingDependency, resolveInstruction.isFailOnResolveError())
            if (!includeProjectDependencies) {
                assertEquals(new DependencyTypeSpec(Type.EXTERNAL), resolveInstruction.dependencySpec)
            }
            returnValue
        }] as ConfigurationResolver
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

    @Override @Test
    public void testExecuteWithEmptyClassifier() {
        prepareDependencyManagerMock(true, true)
        super.testExecuteWithEmptyClassifier();
    }

    @Override @Test
    public void testExecuteWithEmptyAppendix() {
        prepareDependencyManagerMock(true, true)
        super.testExecuteWithEmptyAppendix();
    }

    @Override @Test
    public void testExecute() {
        prepareDependencyManagerMock(true, true)
        super.testExecute();
    }

    @Override @Test(expected = GradleScriptException)
    public void testExecuteWithNullDestinationDir() {
        prepareDependencyManagerMock(true, true)
        super.testExecuteWithNullDestinationDir();
    }


    @Test public void testWar() {
        assertEquals(War.WAR_EXTENSION, war.extension)
    }

    @Test public void testDependencies() {
        prepareDependencyManagerMock(false, false)
        assertEquals(['/file1' as File, '/file2' as File], war.dependencies(false, false))
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