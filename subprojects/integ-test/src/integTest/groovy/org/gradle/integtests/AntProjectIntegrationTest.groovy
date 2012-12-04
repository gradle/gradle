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

import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.util.TestFile
import org.junit.Test
import static org.hamcrest.Matchers.*
import org.gradle.integtests.fixtures.AbstractIntegrationTest

public class AntProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void antTargetsAndGradleTasksCanDependOnEachOther() {
        testFile('build.xml') << """
<project>
    <target name='target1' depends='target2,init'>
        <touch file='build/target1.txt'/>
    </target>
    <target name='target2' depends='init'>
        <touch file='build/target2.txt'/>
    </target>
</project>
"""
        testFile('build.gradle') << """
ant.importBuild(file('build.xml'))
task init << { buildDir.mkdirs() }
task ant(dependsOn: target1)
"""
        TestFile target1File = testFile('build/target1.txt')
        TestFile target2File = testFile('build/target2.txt')
        target1File.assertDoesNotExist()
        target2File.assertDoesNotExist()

        inTestDirectory().withTasks('ant').run().assertTasksExecuted(':init', ':target2', ':target1', ':ant')

        target1File.assertExists()
        target2File.assertExists()
    }

    @Test
    public void canImportMultipleBuildFilesWithDifferentBaseDirs() {
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
    public void handlesAntImportsOk() {
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
    public void reportsAntBuildParseFailure() {
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
    public void reportsAntTaskExecutionFailure() {
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
}
