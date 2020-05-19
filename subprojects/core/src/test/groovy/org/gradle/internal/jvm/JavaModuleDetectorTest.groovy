/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.jvm

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.internal.TestFileContentCacheFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JarUtils
import org.junit.Rule
import spock.lang.Specification

class JavaModuleDetectorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(JavaModuleDetectorTest)

    TestFileContentCacheFactory cacheFactory = new TestFileContentCacheFactory()
    JavaModuleDetector moduleDetector = new JavaModuleDetector(cacheFactory, TestFiles.fileCollectionFactory())

    def "detects modules on classpath"() {
        def path = path('lib.jar', 'module.jar', 'classes', 'classes-module', 'automaticModule.jar', 'mrjarModule.jar')

        expect:
        inferClasspath(path) == ['lib.jar', 'classes']
        inferModulePath(path) == ['module.jar', 'classes-module', 'automaticModule.jar', 'mrjarModule.jar']
    }

    def "filters out directories that do not exist"() {
        def path = path('resources1', 'nothing')

        expect:
        inferClasspath(path) == []
        inferModulePath(path) == []
    }

    def "caches results of module detection"() {
        when:
        def p = path('lib.jar', 'module.jar', 'classes', 'classes-module', 'automaticModule.jar')

        then:
        inferClasspath(p) == ['lib.jar', 'classes']
        inferModulePath(p) == ['module.jar', 'classes-module', 'automaticModule.jar']
        inferClasspath(p) == ['lib.jar', 'classes']
        inferModulePath(p) == ['module.jar', 'classes-module', 'automaticModule.jar']
        cacheFactory.calculationLog == p as List
    }

    List<String> inferClasspath(FileCollection entries) {
        moduleDetector.inferClasspath(true, entries).collect { it.name as String }
    }

    List<String> inferModulePath(FileCollection entries) {
        moduleDetector.inferModulePath(true, entries).collect { it.name as String }
    }

    FileCollection path(String... entries) {
        Set<File> files = entries.collect { entry ->
            if (entry.endsWith('.jar')) {
                def jar = tmpDir.file(entry)
                def manifest = ['Manifest-Version: 1.0']
                if (entry.startsWith('automatic')) {
                    manifest += 'Automatic-Module-Name: auto'
                }
                if (entry.startsWith('mrjar')) {
                    manifest += 'Multi-Release: true'
                }
                if (entry.startsWith('module')) {
                    jar << JarUtils.jarWithContents(('META-INF/MANIFEST.MF'): manifest.join('\n') + '\n', ('module-info.class'): '')
                } else if (entry.startsWith('mrjar')) {
                    jar << JarUtils.jarWithContents(('META-INF/MANIFEST.MF'): manifest.join('\n') + '\n', ('META-INF/versions/10/module-info.class'): '')
                } else {
                    jar << JarUtils.jarWithContents(('META-INF/MANIFEST.MF'): manifest.join('\n') + '\n')
                }
                jar
            } else if (entry.startsWith('classes')) {
                def classes = tmpDir.createDir(entry)
                if (entry.endsWith('module')) {
                    tmpDir.createFile("$entry/module-info.class")
                }
                classes
            } else {
                new File(entry) // do not create
            }
        }
        new TestCollection(files)
    }

    private static class TestCollection extends AbstractFileCollection {
        private Set<File> entries

        private TestCollection(Set<File> entries) {
            this.entries = entries
        }
        @Override
        String getDisplayName() {
            return "test classpath"
        }

        @Override
        Set<File> getFiles() {
            return entries
        }
    }
}
