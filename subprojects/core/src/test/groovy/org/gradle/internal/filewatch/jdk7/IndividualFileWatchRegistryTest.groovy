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

import spock.lang.Specification

import java.nio.file.Path


class IndividualFileWatchRegistryTest extends Specification {
    Map<File, Path> dirToPathMocks = [:]

    def "registering individual files"() {
        given:
        WatchStrategy watchStrategy = Mock(WatchStrategy)
        IndividualFileWatchRegistry watchRegistry = new IndividualFileWatchRegistry(watchStrategy) {
            @Override
            protected Path dirToPath(File dir) {
                Path p = dirToPathMocks.get(dir)
                if(p == null) {
                    throw new RuntimeException("Mock missing for " + dir)
                }
                p
            }
        }
        def file1 = new File("a/b/c") {
            File getAbsoluteFile() {
                return this
            }
        }
        def file2 = new File("a/b/d") {
            File getAbsoluteFile() {
                return this
            }
        }
        def file3 = new File("a/e/f") {
            File getAbsoluteFile() {
                return this
            }
        }
        def parent1 = file1.getParentFile()
        def parent2 = file3.getParentFile()
        def parentPath1 = Mock(Path)
        dirToPathMocks.put(parent1, parentPath1)
        def parentPath2 = Mock(Path)
        dirToPathMocks.put(parent2, parentPath2)
        when: 'multiple files are registered'
        watchRegistry.register('sourcekey', [file1, file2, file3])
        then: 'should register only unique parent directories'
        1 * watchStrategy.watchSingleDirectory( { it.is(parentPath1) })
        1 * watchStrategy.watchSingleDirectory( { it.is(parentPath2) } )
        0 * _._
    }
}
