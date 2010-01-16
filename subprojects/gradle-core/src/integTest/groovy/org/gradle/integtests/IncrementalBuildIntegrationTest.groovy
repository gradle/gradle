/*
 * Copyright 2010 the original author or authors.
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

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.util.TestFile
import org.gradle.util.TestFile.Snapshot

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

        TestFile.Snapshot aSnapshot = outputFileA.snapshot()
        TestFile.Snapshot bSnapshot = outputFileB.snapshot()
        assertThat(outputFileA.text, equalTo('[content]'))
        assertThat(outputFileB.text, equalTo('[[content]]'))

        // No changes

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Update timestamp, no content changes

        inputFile.setLastModified(inputFile.lastModified() - 10000);

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Change content

        inputFile.text = 'new content'

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        outputFileA.assertHasChangedSince(aSnapshot)
        outputFileB.assertHasChangedSince(bSnapshot)
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

        // Change build file in a way which does not affect the task

        testFile('build.gradle').text += '''
task c
'''

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        // Change build file

        testFile('build.gradle').text += '''
b.outputFile = file('new-output.txt')
'''

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a')

        // Run with --no-opt command-line options
        inTestDirectory().withTasks('b').withArguments('--no-opt').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()
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
        TestFile.Snapshot aSnapshot = outputAFile.snapshot()
        TestFile.Snapshot bSnapshot = outputBFile.snapshot()

        outputAFile.assertContents(equalTo('[content]'))
        outputBFile.assertContents(equalTo('[[content]]'))

        // No changes

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        outputAFile.assertHasNotChangedSince(aSnapshot)
        outputBFile.assertHasNotChangedSince(bSnapshot)

        // Change content

        testFile('src/file1.txt').write('new content')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()
        
        outputAFile.assertHasChangedSince(aSnapshot)
        outputBFile.assertHasChangedSince(bSnapshot)
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

    @Test
    public void multipleTasksCanGenerateIntoOverlappingOutputDirectories() {
        testFile('build.gradle') << '''
task a(type: org.gradle.integtests.DirTransformerTask) {
    inputDir = file('src/a')
    outputDir = file('build')
}
task b(type: org.gradle.integtests.DirTransformerTask) {
    inputDir = file('src/b')
    outputDir = file('build')
}
'''

        testFile('src/a').mkdirs()
        testFile('src/b').mkdirs()
        testFile('src/a/file1.txt').write('content')
        testFile('src/b/file2.txt').write('content')

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        // No changes

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        // Delete an output file

        testFile('build/file1.txt').delete()

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':b')

        // Change an output file

        testFile('build/file1.txt').write('something else')

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':b')
    }

    @Test
    public void canUseUpToDatePredicateToForceTaskToExecute() {
        testFile('build.gradle') << '''
task a(type: org.gradle.integtests.TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('src.a.txt')
    outputs.upToDateWhen { false }
}
'''
        TestFile inputFile = testFile('src.txt')
        TestFile outputFile = testFile('src.a.txt')

        inputFile.text = 'content'

        inTestDirectory().withTasks('a').run().assertTasksExecuted(':a').assertTasksSkipped()

        Snapshot outputFileState = outputFile.snapshot()

        inTestDirectory().withTasks('a').run().assertTasksExecuted(':a').assertTasksSkipped()

        outputFile.assertHasChangedSince(outputFileState)
    }
}