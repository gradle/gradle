package org.gradle.integtests

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class IncrementalBuildIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void skipsTaskWhenInputFilesHaveNotChanged() {
        testFile('build.gradle') << '''
task a(type: org.gradle.integtests.TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('src.a.txt')
}
task b(type: org.gradle.integtests.TransformerTask, dependsOn: a) {
    inputFile = a.outputFile
    outputFile = file('src.b.txt')
}
'''
        TestFile inputFile = testFile('src.txt')
        TestFile outputFileA = testFile('src.a.txt')
        TestFile outputFileB = testFile('src.b.txt')

        inputFile.text = 'content'

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        long modTimeA = outputFileA.lastModified()
        long modTimeB = outputFileB.lastModified()
        assertThat(outputFileA.text, equalTo('[content]'))
        assertThat(outputFileB.text, equalTo('[[content]]'))

        // No changes

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        assertThat(outputFileA.text, equalTo('[content]'))
        assertThat(outputFileB.text, equalTo('[[content]]'))
        assertThat(outputFileA.lastModified(), equalTo(modTimeA))
        assertThat(outputFileB.lastModified(), equalTo(modTimeB))

        // Update timestamp, no content changes

        inputFile.setLastModified(modTimeA - 10000);

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        assertThat(outputFileA.text, equalTo('[content]'))
        assertThat(outputFileB.text, equalTo('[[content]]'))
        assertThat(outputFileA.lastModified(), equalTo(modTimeA))
        assertThat(outputFileB.lastModified(), equalTo(modTimeB))

        // Change content

        inputFile.text = 'new content'

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        assertThat(outputFileA.text, equalTo('[new content]'))
        assertThat(outputFileB.text, equalTo('[[new content]]'))

        // Delete intermediate output file

        outputFileA.delete()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':b')

        assertThat(outputFileA.text, equalTo('[new content]'))
        assertThat(outputFileB.text, equalTo('[[new content]]'))

        // Delete final output file

        outputFileB.delete()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a')

        assertThat(outputFileA.text, equalTo('[new content]'))
        assertThat(outputFileB.text, equalTo('[[new content]]'))

        // Change build file in non-material way

        testFile('build.gradle').text += '''
task c
'''

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        // Change build file

        testFile('build.gradle').text += '''
b.outputFile = file('new-output.txt')
'''

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a')
    }

    @Test
    public void skipsTaskWhenInputDirContentsHaveNotChanged() {
        testFile('build.gradle') << '''
task a(type: org.gradle.integtests.DirTransformerTask) {
    inputDir = file('src')
    outputDir = file('build/a')
}
task b(type: org.gradle.integtests.DirTransformerTask, dependsOn: a) {
    inputDir = a.outputDir
    outputDir = file('build/b')
}
'''

        testFile('src').createDir()
        testFile('src/file1.txt').write('content')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        TestFile outputAFile = testFile('build/a/file1.txt')
        TestFile outputBFile = testFile('build/b/file1.txt')
        long outputAModTime = outputAFile.lastModified()
        long outputBModTime = outputBFile.lastModified()

        outputAFile.assertContents(equalTo('[content]'))
        outputBFile.assertContents(equalTo('[[content]]'))

        // No changes

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        assertEquals(outputAModTime, outputAFile.lastModified())
        assertEquals(outputBModTime, outputBFile.lastModified())

        outputAFile.assertContents(equalTo('[content]'))
        outputBFile.assertContents(equalTo('[[content]]'))

        // Change content

        testFile('src/file1.txt').write('new content')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()
        
        outputAFile.assertContents(equalTo('[new content]'))
        outputBFile.assertContents(equalTo('[[new content]]'))

        // Add file

        testFile('src/file2.txt').write('content2')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        testFile('build/a/file2.txt').assertContents(equalTo('[content2]'))
        testFile('build/b/file2.txt').assertContents(equalTo('[[content2]]')) 

        // Remove file

        testFile('src/file1.txt').delete()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':b')
    }
}