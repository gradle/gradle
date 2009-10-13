package org.gradle.integtests

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class IncrementalBuildIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void skipsTaskWhenInputsHaveNotChanged() {
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
}