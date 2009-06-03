package org.gradle.integtests

import org.junit.Test
import org.junit.Assert
import org.junit.Ignore

public class AntProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void antTargetsAndGradleTasksCanDependOnEachOther() {
        testFile('build.xml').asFile() << """
<project>
    <target name='target1' depends='target2,init'>
        <touch file='build/target1.txt'/>
    </target>
    <target name='target2' depends='init'>
        <touch file='build/target2.txt'/>
    </target>
</project>
"""
        testFile('build.gradle').asFile() << """
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

    @Test @Ignore
    public void canImportMultipleBuildFiles() {
        Assert.fail()
    }

    @Test @Ignore
    public void handlesAntImportsOk() {
        Assert.fail()
    }

    @Test @Ignore
    public void reportsAntBuildParseFailure() {
        Assert.fail()
    }

    @Test @Ignore
    public void reportsAntTaskExecutionFailure() {
        Assert.fail()
    }
}