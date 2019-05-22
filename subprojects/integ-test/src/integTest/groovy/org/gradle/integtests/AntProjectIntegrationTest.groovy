/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.*
import static org.hamcrest.CoreMatchers.startsWith

class AntProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    void antTargetsAndGradleTasksCanDependOnEachOther() {
        testFile('build.xml') << """
<project>
    <target name='target1' depends='target2,initialize'>
        <touch file='build/target1.txt'/>
    </target>
    <target name='target2' depends='initialize'>
        <touch file='build/target2.txt'/>
    </target>
</project>
"""
        testFile('build.gradle') << """
ant.importBuild(file('build.xml'))
task initialize { doLast { buildDir.mkdirs() } }
task ant(dependsOn: target1)
"""
        TestFile target1File = testFile('build/target1.txt')
        TestFile target2File = testFile('build/target2.txt')
        target1File.assertDoesNotExist()
        target2File.assertDoesNotExist()

        inTestDirectory().withTasks('ant').run().assertTasksExecutedInOrder(':initialize', ':target2', ':target1', ':ant')

        target1File.assertExists()
        target2File.assertExists()
    }

    @Test
    void canImportMultipleBuildFilesWithDifferentBaseDirs() {
        testFile('project1/build.xml') << """
<project>
    <target name='target1'>
        <mkdir dir='build'/>
        <touch file='build/target1.txt'/>
    </target>
</project>
"""
        testFile('project2/build.xml') << """
<project>
    <target name='target2'>
        <mkdir dir='build'/>
        <touch file='build/target2.txt'/>
    </target>
</project>
"""
        testFile('build.gradle') << """
ant.importBuild('project1/build.xml')
ant.importBuild('project2/build.xml')
task ant(dependsOn: [target1, target2])
"""
        TestFile target1File = testFile('project1/build/target1.txt')
        TestFile target2File = testFile('project2/build/target2.txt')
        target1File.assertDoesNotExist()
        target2File.assertDoesNotExist()

        inTestDirectory().withTasks('ant').run().assertTasksExecuted(':target1', ':target2', ':ant')

        target1File.assertExists()
        target2File.assertExists()
    }

    @Test
    void handlesAntImportsOk() {
        testFile('imported.xml') << """
<project>
    <target name='target1'>
        <mkdir dir='build'/>
        <touch file='build/target1.txt'/>
    </target>
</project>
"""
        testFile('build.xml') << """
<project>
    <import file="imported.xml"/>
    <target name='target2'>
        <mkdir dir='build'/>
        <touch file='build/target2.txt'/>
    </target>
</project>
"""
        testFile('build.gradle') << """
ant.importBuild('build.xml')
task ant(dependsOn: [target1, target2])
"""
        TestFile target1File = testFile('build/target1.txt')
        TestFile target2File = testFile('build/target2.txt')
        target1File.assertDoesNotExist()
        target2File.assertDoesNotExist()

        inTestDirectory().withTasks('ant').run().assertTasksExecuted(':target1', ':target2', ':ant')

        target1File.assertExists()
        target2File.assertExists()
    }

    @Test
    void reportsAntBuildParseFailure() {
        TestFile antBuildFile = testFile('build.xml')
        antBuildFile << """
<project>
    <target name='target1'
        <unknown/>
    </target>
</project>
"""
        TestFile buildFile = testFile('build.gradle')
        buildFile << """
ant.importBuild('build.xml')
"""
        ExecutionFailure failure = inTestDirectory().withTasks('target1').runWithFailure()
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertThatDescription(startsWith('A problem occurred evaluating root project'))
        failure.assertHasCause("Could not import Ant build file '$antBuildFile'.")
    }

    @Test
    void reportsAntTaskExecutionFailure() {
        testFile('build.xml') << """
<project>
    <target name='target1'>
        <fail>broken</fail>
    </target>
</project>
"""
        TestFile buildFile = testFile('build.gradle')
        buildFile << """
ant.importBuild('build.xml')
"""
        ExecutionFailure failure = inTestDirectory().withTasks('target1').runWithFailure()
        failure.assertHasDescription('Execution failed for task \':target1\'.')
        failure.assertHasCause('broken')
    }

    @Test
    void targetDependenciesAreOrderedBasedOnDeclarationSequence() {
        testFile('build.xml') << """
<project>
    <target name='a' depends='d,c,b'/>
    <target name='b'/>
    <target name='c'/>
    <target name='d'/>
    <target name='e' depends='g,f'/>
    <target name='f'/>
    <target name='g'/>
    <target name='h' depends='i'/>
    <target name='i'/>
</project>
"""
        testFile('build.gradle') << """
ant.importBuild('build.xml')
"""
        inTestDirectory().withTasks('a', 'e', 'h').run()
            .assertTasksExecutedInOrder any(
                exact(any(':d', ':c', ':b'), ':a'),
                exact(any(':g', ':f'), ':e'),
                exact(':i', ':h')
            )
    }

    @Test
    void targetDependenciesOrderDoesNotCreateCycle() {
        testFile('build.xml') << """
<project>
    <target name='a' depends='c,b'/>
    <target name='b'/>
    <target name='c' depends='b'/>
</project>
"""
        testFile('build.gradle') << """
ant.importBuild('build.xml')
"""
        inTestDirectory().withTasks('a').run().assertTasksExecutedInOrder(':b', ':c', ':a')
    }

    @Test
    void unknownDependencyProducesUsefulMessage() {
        testFile('build.xml') << """
<project>
    <target name='a' depends='b'/>
</project>
"""
        testFile('build.gradle') << """
ant.importBuild('build.xml')
"""
        inTestDirectory().withTasks('a').runWithFailure().assertHasCause("Imported Ant target 'a' depends on target or task 'b' which does not exist")
    }

    @Test
    void canHandleDependencyOrderingBetweenNonExistentTasks() {
        testFile('build.xml') << """
<project>
    <target name='a' depends='b,c'/>
</project>
"""
        testFile('build.gradle') << """
ant.importBuild('build.xml')
"""
        // Testing that we don't get some obscure error message trying to set c.shouldRunAfter b
        inTestDirectory().withTasks('a').runWithFailure().assertHasCause("Imported Ant target 'a' depends on target or task 'b' which does not exist")
    }

    @Test
    void canApplyJavaPluginWithAntBuild() {
        testFile('build.xml') << """
<project>
    <target name='clean'>
        <echo message='Executing Ant clean'/>
    </target>
    <target name='target2' depends='clean'/>
    <target name='target1' depends='target2'/>
</project>
"""
        testFile('build.gradle') << """
apply plugin:'java'
ant.importBuild(file('build.xml')) { antTaskName ->
    'ant-'+antTaskName
}

task ant(dependsOn: 'ant-target1')
"""
        inTestDirectory().withTasks('clean', 'ant').run().assertTasksExecutedInOrder(':clean', ':ant-clean', ':ant-target2', ':ant-target1', ':ant')

    }

    @Test
    void canRenameAntDelegateTask() {
        testFile('build.xml') << """
<project>
    <target name='c'/>
    <target name='b' depends='c'/>
    <target name='a' depends='b'/>
</project>
"""
        testFile('build.gradle') << """

ant.importBuild(file('build.xml')) { antTaskName ->
    antTaskName == 'b' ? 'ant-b' : antTaskName
}

task runAnt(dependsOn: 'a')
"""
        inTestDirectory().withTasks('runAnt').run().assertTasksExecutedInOrder(':c', ':ant-b', ':a', ':runAnt')

    }
}
