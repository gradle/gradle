/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AllFromJarRebuildInfoTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def "empty jar"() {
        def classes = new FileTreeAdapter(new DirectoryFileTree(new File("missing")))

        expect:
        new AllFromJarRebuildInfo(new JarArchive(new File("j"), classes)).changedClassesInJar.isEmpty()
    }

    def "contains all classes"() {
        temp.createFile("root/com/foo/Foo.class")
        temp.createFile("root/com/Bar.class")
        def classes = new FileTreeAdapter(new DirectoryFileTree(temp.file("root")))

        expect:
        new AllFromJarRebuildInfo(new JarArchive(new File("j"), classes)).changedClassesInJar == ["com.foo.Foo", "com.Bar"] as Set
    }
}
