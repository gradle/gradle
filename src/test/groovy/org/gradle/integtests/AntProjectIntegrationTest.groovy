package org.gradle.integtests

import org.junit.Test
import static org.hamcrest.Matchers.*

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
usePlugin('ant')
importAntBuild(file('build.xml'))
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
usePlugin('ant')
importAntBuild('project1/build.xml')
importAntBuild('project2/build.xml')
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
usePlugin('ant')
importAntBuild('build.xml')
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
usePlugin('ant')
importAntBuild('build.xml')
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
usePlugin('ant')
importAntBuild('build.xml')
"""
        ExecutionFailure failure = inTestDirectory().withTasks('target1').runWithFailure()
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasDescription('Execution failed for task \':target1\'.')
        failure.assertHasCause('broken')
    }
}