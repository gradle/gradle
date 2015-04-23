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
import spock.lang.Specification

import java.nio.file.DirectoryStream
import java.nio.file.FileSystem
import java.nio.file.FileVisitor
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.spi.FileSystemProvider

class DirTreeWatchRegistryTest extends Specification {
    Map<File, Path> dirToPathMocks = [:]

    def "registering directory"() {
        given:
        WatchStrategy watchStrategy = Mock(WatchStrategy)
        DirTreeWatchRegistry watchRegistry = new DirTreeWatchRegistry(watchStrategy) {
            @Override
            protected Path dirToPath(File dir) {
                Path p = dirToPathMocks.get(dir)
                if(p == null) {
                    throw new RuntimeException("Mock missing for " + dir)
                }
                p
            }

            @Override
            protected Path walkFileTree(Path start, FileVisitor<? super Path> visitor) throws IOException {
                visitor.preVisitDirectory(start, null);
                start
            }
        }
        DirectoryTree tree = Mock(DirectoryTree)

        def dir = new File("a2/b2")

        def fileSystemProvider = Mock(FileSystemProvider)
        def fileSystem = Mock(FileSystem)
        def dirPathMock = Mock(Path)
        dirToPathMocks.put(dir, dirPathMock)

        def dirAttributes  = Mock(BasicFileAttributes)
        def directoryStream = Mock(DirectoryStream)

        when:
        watchRegistry.register('sourcekey', [tree])
        then: 'results in watchSingleDirectory call'
        tree.getDir() >> dir
        tree.getPatterns() >> new PatternSet()

        dirPathMock.toFile() >> dir
        dirPathMock.relativize(_) >> { Path param ->
            param
        }

        1 * watchStrategy.watchSingleDirectory(dirPathMock)

        0 * _._
    }
}
