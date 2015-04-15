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

package org.gradle.internal.filewatch.jdk7

import org.gradle.api.file.DirectoryTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.filewatch.FileWatcher
import spock.lang.Specification

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class FileWatcherExecutorTest extends Specification {
    def fileWatcher
    def runningFlag
    Runnable callback
    def directories
    def files
    def watchService
    def fileWatcherExecutor
    int maxWatchLoops = 0
    Map<File, Path> dirToPathMocks = [:]

    def setup() {
        fileWatcher = Mock(FileWatcher)
        runningFlag = new AtomicBoolean(true)
        callback = Mock(Runnable)
        directories = []
        files = []
        watchService = Mock(WatchService)
        fileWatcherExecutor = new FileWatcherExecutor(fileWatcher, runningFlag, callback, directories, files, new CountDownLatch(1)) {
            int watchLoopCounter = 0

            @Override
            protected FileWatcherChangesNotifier createChangesNotifier(FileWatcher fileWatcher, Runnable callback) {
                return new FileWatcherChangesNotifier(fileWatcher, callback) {
                    @Override
                    protected boolean quietPeriodBeforeNotifyingHasElapsed() {
                        return true
                    }
                }
            }

            @Override
            protected boolean supportsWatchingSubTree() {
                return false
            }

            @Override
            protected WatchService createWatchService() {
                return watchService
            }

            @Override
            protected Path dirToPath(File dir) {
                Path p = dirToPathMocks.get(dir)
                if(p == null) {
                    p = Mock(Path)
                    dirToPathMocks.put(dir, p)
                }
                p
            }

            @Override
            protected boolean watchLoopRunning() {
                watchLoopCounter++ < maxWatchLoops
            }
        }
    }

    def "test FileWatcherExecutor interaction with WatchService"() {
        given:
        def file = new File("a/b/c")
        files << file
        def filePathMock = Mock(Path)
        dirToPathMocks.put(file.getParentFile(), filePathMock)
        def fileWatchKey = Mock(WatchKey)

        def dir = new File("a2/b2")
        def directoryTree = Mock(DirectoryTree)
        directoryTree.getDir() >> dir
        directoryTree.getPatterns() >> new PatternSet()
        directories << directoryTree
        def dirPathMock = Mock(Path)

        def fileSystemProvider = Mock(FileSystemProvider)
        def fileSystem = Mock(FileSystem)
        fileSystem.provider() >> fileSystemProvider

        dirPathMock.getFileSystem() >> fileSystem
        dirPathMock.toFile() >> dir
        dirPathMock.toString() >> "a2/b2"
        dirPathMock.relativize(_) >> { Path param ->
            param
        }

        dirToPathMocks.put(dir, dirPathMock)

        def dirWatchKey = Mock(WatchKey)

        def subDirPathMock = Mock(Path)
        subDirPathMock.getFileSystem() >> fileSystem
        subDirPathMock.toFile() >> new File(dir, "subdir")

        subDirPathMock.toString() >> "subdir"

        def directoryStream = Mock(DirectoryStream)
        fileSystemProvider.newDirectoryStream(dirPathMock, Files.AcceptAllFilter.FILTER) >> directoryStream
        directoryStream.iterator() >> [subDirPathMock].iterator()

        def subDirStream = Mock(DirectoryStream)
        fileSystemProvider.newDirectoryStream(subDirPathMock, Files.AcceptAllFilter.FILTER) >> subDirStream
        subDirStream.iterator() >> Collections.emptyIterator()

        def subDirWatchKey = Mock(WatchKey)

        maxWatchLoops = 1

        def mockWatchEvent = Mock(WatchEvent)

        def modifiedFilePath = Mock(Path)
        modifiedFilePath.getFileSystem() >> fileSystem

        modifiedFilePath.getParent() >> dirPathMock
        modifiedFilePath.getName() >> 'modifiedfile'
        modifiedFilePath.toString() >> 'modifiedfile'

        def dirAttributes  = Mock(BasicFileAttributes)
        dirAttributes.isDirectory() >> true
        def fileAttributes = Mock(BasicFileAttributes)
        fileAttributes.isDirectory() >> false
        fileSystemProvider.readAttributes(_ as Path, _ as Class, _) >> { path, clazz, options ->
            if(path.toString()=='modifiedfile') {
                fileAttributes
            } else {
                dirAttributes
            }
        }

        when:
        fileWatcherExecutor.run()
        then: 'watches get registered'
        filePathMock.register(watchService, FileWatcherExecutor.WATCH_KINDS, FileWatcherExecutor.WATCH_MODIFIERS) >> fileWatchKey
        1 * dirPathMock.register(watchService, FileWatcherExecutor.WATCH_KINDS, FileWatcherExecutor.WATCH_MODIFIERS) >> dirWatchKey
        1 * subDirPathMock.register(watchService, FileWatcherExecutor.WATCH_KINDS, FileWatcherExecutor.WATCH_MODIFIERS) >> subDirWatchKey

        then: 'watchservice gets polled and returns a modification in a directory'
        watchService.poll(_, _) >> dirWatchKey

        1 * dirWatchKey.watchable() >> dirPathMock
        1 * dirWatchKey.pollEvents() >> [mockWatchEvent]


        mockWatchEvent.kind() >> StandardWatchEventKinds.ENTRY_MODIFY
        mockWatchEvent.context() >> modifiedFilePath

        then: 'relative path gets resolved'
        1 * dirPathMock.resolve(modifiedFilePath) >> { Path other -> other }
        1 * modifiedFilePath.toFile() >> new File(dir, "modifiedfile")

        then: 'WatchKey gets resetted'
        1 * dirWatchKey.reset()
        then: 'callback gets called'
        1 * callback.run()
        then: 'finally watchservice gets closed'
        watchService.close()
    }
}
