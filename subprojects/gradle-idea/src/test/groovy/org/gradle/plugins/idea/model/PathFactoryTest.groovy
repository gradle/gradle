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
package org.gradle.plugins.idea.model

import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class PathFactoryTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()
    final PathFactory factory = new PathFactory()

    def createsPathForAFileUnderARootDir() {
        factory.addPathVariable('ROOT_DIR', tmpDir.dir)

        expect:
        def path = factory.path(tmpDir.file('a', 'b'))
        path.url == 'file://$ROOT_DIR$/a/b'
    }

    def createsPathForAFileNotUnderARootDir() {
        factory.addPathVariable('ROOT_DIR', tmpDir.dir)
        def file = tmpDir.dir.parentFile.file('a')
        def relpath = file.absolutePath.replace(File.separator, '/')

        expect:
        def path = factory.path(file)
        path.url == "file://$relpath"
    }

    def usesTheClosestAncestorRootDirForAFileUnderMultipleRootDirs() {
        factory.addPathVariable('ROOT_DIR', tmpDir.dir)
        factory.addPathVariable('SUB_DIR', tmpDir.file('sub'))

        expect:
        def path = factory.path(tmpDir.file('sub', 'a'))
        path.url == 'file://$SUB_DIR$/a'
    }

    def createsPathForARootDir() {
        factory.addPathVariable('SUB_DIR', tmpDir.file('sub'))
        factory.addPathVariable('ROOT_DIR', tmpDir.dir)

        expect:
        def path = factory.path(tmpDir.dir)
        path.url == 'file://$ROOT_DIR$/'

        path = factory.path(tmpDir.file('sub'))
        path.url == 'file://$SUB_DIR$/'
    }

    def createsPathForAUrl() {
        expect:
        def path = factory.path('file://a/b/c')
        path.url == 'file://a/b/c'
    }
}
