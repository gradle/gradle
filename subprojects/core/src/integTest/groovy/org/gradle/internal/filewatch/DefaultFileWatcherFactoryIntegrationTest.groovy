/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * integration tests for {@link org.gradle.internal.filewatch.DefaultFileWatcherFactory}
 */
@IgnoreIf({ !JavaVersion.current().java7Compatible })
class DefaultFileWatcherFactoryIntegrationTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    DefaultFileWatcherFactory fileWatcherFactory
    File testDir
    long waitForEventsMillis = OperatingSystem.current().isMacOsX() ? 3500L : 1500L
    FileWatcher fileWatcher
    FileWatchInputs fileWatchInputs

    void setup() {
        NativeServicesTestFixture.initialize()
        fileWatcherFactory = new DefaultFileWatcherFactory(new DefaultExecutorFactory())
        fileWatcher = fileWatcherFactory.createFileWatcher()
        fileWatchInputs = new DefaultFileWatchInputs()
        fileWatchInputs.watch(new DirectoryFileTree(testDir.getTestDirectory()))
    }

    void cleanup() {
        fileWatcher.stop()
        fileWatcherFactory.stop()
    }

    def "watch service should notify of new files"() {
        given:
        def fileWatchListener = Mock(FileWatchListener)
        when:
        fileWatcher.watch(fileWatchInputs, fileWatchListener)
        File createdFile = testDir.file("newfile.txt")
        createdFile.text = "Hello world"
        waitForChanges()
        then:
        1 * fileWatchListener.changesDetected(_)
    }

    private void waitForChanges() {
        sleep(waitForEventsMillis)
    }

    def "watch service should use default excludes"() {
        given:
        def fileWatchListener = Mock(FileWatchListener)
        when:
        fileWatcher.watch(fileWatchInputs, fileWatchListener)
        TestFile gitDir = testDir.createDir(".git")
        File createdFile = gitDir.file("some_git_object")
        createdFile.text = "some git data here, shouldn't trigger a change event"
        waitForChanges()
        then:
        0 * fileWatchListener.changesDetected(_)
    }

    def "watch service should notify of new files in subdirectories"() {
        given:
        def fileWatchListener = Mock(FileWatchListener)
        when:
        fileWatcher.watch(fileWatchInputs, fileWatchListener)
        def subdir = testDir.createDir("subdir")
        subdir.createFile("somefile").text = "Hello world"
        waitForChanges()
        then:
        1 * fileWatchListener.changesDetected(_)
        when:
        subdir.file('someotherfile').text = "Hello world"
        waitForChanges()
        then:
        1 * fileWatchListener.changesDetected(_)
    }

    def "default ignored files shouldn't trigger changes"() {
        given:
        def fileWatchListener = Mock(FileWatchListener)
        when:
        fileWatcher.watch(fileWatchInputs, fileWatchListener)
        testDir.file('some_temp_file~').text = "This change should be ignored"
        waitForChanges()
        then:
        0 * fileWatchListener.changesDetected(_)
    }

    def "excluded subdirectory should not be listened for changes"() {
        given:
        fileWatchInputs = new DefaultFileWatchInputs()
        PatternSet patternSet = new PatternSet()
        patternSet.exclude("a/b/**")
        fileWatchInputs.watch(new DirectoryFileTree(testDir.getTestDirectory(), patternSet))
        def fileWatchListener = Mock(FileWatchListener)
        when:
        fileWatcher.watch(fileWatchInputs, fileWatchListener)
        testDir.createDir('a/b/2').file('some_file').text = "This change should not be noticed"
        waitForChanges()
        then:
        0 * fileWatchListener.changesDetected(_)
    }

    def "creating an empty directories should not trigger changes"() {
        given:
        def fileWatchListener = Mock(FileWatchListener)
        when:
        fileWatcher.watch(fileWatchInputs, fileWatchListener)
        testDir.createDir('a/b/c')
        testDir.createDir('b')
        testDir.createDir('c/d/e/f/g')
        waitForChanges()
        then:
        0 * fileWatchListener.changesDetected(_)
        when: 'file is created in subdir'
        testDir.file('a/b/c').file('somefile').text = 'file'
        waitForChanges()
        then: 'it should trigger a change event'
        1 * fileWatchListener.changesDetected(_)
    }

    def "watching individual files should watch for modifications"() {
        given:
        def fileWatchListener = Mock(FileWatchListener)
        fileWatchInputs = new DefaultFileWatchInputs()
        def subdir1 = testDir.createDir('a/b/c')
        def watchedfile1 = subdir1.file('file1')
        watchedfile1.text = 'watchedfile1 content'
        def watchedfile2 = subdir1.file('file2')
        watchedfile2.text = 'watchedfile2 content'
        fileWatchInputs.watch(watchedfile1)
        fileWatchInputs.watch(watchedfile2)
        def nonwatchedfile1 = subdir1.file('file3')
        when:
        fileWatcher.watch(fileWatchInputs, fileWatchListener)
        nonwatchedfile1.text = 'some change'
        waitForChanges()
        then:
        0 * fileWatchListener.changesDetected(_)
        when:
        watchedfile1.text = 'some changes'
        waitForChanges()
        then:
        1 * fileWatchListener.changesDetected(_)
        when:
        watchedfile2.text = 'some more changes'
        waitForChanges()
        then:
        1 * fileWatchListener.changesDetected(_)
        when:
        watchedfile1.delete()
        waitForChanges()
        then:
        1 * fileWatchListener.changesDetected(_)
    }

    def "existing files shouldn't trigger changes"() {
        given:
        def fileWatchListener = Mock(FileWatchListener)
        testDir.createDir('a/b/c').with { dir ->
            (1..10).each {
                dir.file(it.toString()).text = "content for ${it}"
            }
        }
        when:
        fileWatcher.watch(fileWatchInputs, fileWatchListener)
        waitForChanges()
        then:
        0 * fileWatchListener.changesDetected(_)
        when: 'existing file is modified'
        testDir.file('a/b/c/1').text = 'file was modified'
        waitForChanges()
        then: 'it should trigger a change'
        1 * fileWatchListener.changesDetected(_)
        when: 'existing file is deleted'
        testDir.file('a/b/c/2').with {
            assert it.exists()
            it.delete()
        }
        waitForChanges()
        then: 'it should trigger a change'
        1 * fileWatchListener.changesDetected(_)
    }

}
