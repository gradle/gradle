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

import org.gradle.internal.filewatch.FileWatcherListener
import spock.lang.Specification

import java.nio.file.AccessDeniedException
import java.nio.file.FileSystem
import java.nio.file.FileSystemException
import java.nio.file.Path
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.spi.FileSystemProvider

class WatchServiceRegistrarTest extends Specification {
    def fileSystem = Stub(org.gradle.internal.nativeintegration.filesystem.FileSystem)

    def "registering gets retried"() {
        given:
        WatchService watchService = Mock()
        WatchServiceRegistrar registrar = new WatchServiceRegistrar(watchService, Mock(FileWatcherListener), fileSystem)
        def rootDirPath = Mock(Path)
        def watchKey = Mock(WatchKey)

        when:
        registrar.watchDir(rootDirPath)

        then:
        2 * rootDirPath.register(_, _, _) >> {
            throw new FileSystemException("Bad file descriptor")
        } >> {
            watchKey
        }
    }

    def "exception gets thrown after retrying once"() {
        given:
        WatchService watchService = Mock()
        WatchServiceRegistrar registrar = new WatchServiceRegistrar(watchService, Mock(FileWatcherListener), fileSystem)
        def rootDirPath = Mock(Path)

        when:
        registrar.watchDir(rootDirPath)

        then:
        2 * rootDirPath.register(_, _, _) >> {
            throw new FileSystemException("Bad file descriptor")
        } >> {
            throw new FileSystemException("Bad file descriptor")
        }
        thrown(FileSystemException)
    }

    def "silently ignore exception for deleted files"() {
        given:
        WatchService watchService = Mock()
        WatchServiceRegistrar registrar = new WatchServiceRegistrar(watchService, Mock(FileWatcherListener), fileSystem)
        def rootDirPath = Mock(Path)
        def fileSystem = Mock(FileSystem)
        def fileSystemProvider = Mock(FileSystemProvider)
        rootDirPath.getFileSystem() >> fileSystem
        fileSystem.provider() >> fileSystemProvider
        fileSystemProvider.checkAccess(_, _) >> { throw new FileNotFoundException("File doesn't exist") }

        when:
        registrar.watchDir(rootDirPath)

        then:
        1 * rootDirPath.register(_, _, _) >> {
            throw new AccessDeniedException("Access denied")
        }
        noExceptionThrown()
    }

    def "rethrow without retrying"() {
        given:
        WatchService watchService = Mock()
        WatchServiceRegistrar registrar = new WatchServiceRegistrar(watchService, Mock(FileWatcherListener), fileSystem)
        def rootDirPath = Mock(Path)
        def fileSystem = Mock(FileSystem)
        def fileSystemProvider = Mock(FileSystemProvider)
        rootDirPath.getFileSystem() >> fileSystem
        fileSystem.provider() >> fileSystemProvider

        when:
        registrar.watchDir(rootDirPath)

        then:
        1 * rootDirPath.register(_, _, _) >> {
            throw new IOException("Cannot watch file")
        }
        thrown(IOException)
    }
}
