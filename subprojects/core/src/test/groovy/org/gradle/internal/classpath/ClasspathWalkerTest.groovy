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

package org.gradle.internal.classpath

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.file.FileException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

class ClasspathWalkerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(ClasspathWalkerTest.class)
    def walker = new ClasspathWalker(TestFiles.fileSystem())

    def "skips missing file"() {
        def visitor = Mock(ClasspathEntryVisitor)

        when:
        walker.visit(tmpDir.file("missing"), visitor)

        then:
        0 * visitor._
    }

    @Requires(UnitTestPreconditions.Jdk11OrLater)
    def "throws FileException on badly formed JAR"() {
        def visitor = Mock(ClasspathEntryVisitor)
        def file = tmpDir.file("broken").createFile()

        when:
        walker.visit(file, visitor)

        then:
        thrown(FileException)

        0 * visitor._
    }

    // This documents current behaviour, not desired behaviour
    @Requires(UnitTestPreconditions.Jdk10OrEarlier)
    def "ignores badly formed JAR"() {
        def visitor = Mock(ClasspathEntryVisitor)
        def file = tmpDir.file("broken").createFile()

        when:
        walker.visit(file, visitor)

        then:
        0 * visitor._
    }

    def "visits files of directory tree"() {
        def visitor = Mock(ClasspathEntryVisitor)

        def dir = tmpDir.createDir("dir")
        dir.file("empty").createDir()
        dir.file("a.class").text = "a"
        dir.file("a/b/c.class").text = "c"
        dir.file("a/b/empty").createDir()

        when:
        walker.visit(dir, visitor)

        then:
        1 * visitor.visit({ it.name == "a.class" }) >> { ClasspathEntryVisitor.Entry entry ->
            assert entry.path.toString() == "a.class"
            assert Arrays.equals(entry.content, "a".bytes)
        }
        1 * visitor.visit({ it.name == "a/b/c.class" }) >> { ClasspathEntryVisitor.Entry entry ->
            assert entry.path.toString() == "a/b/c.class"
            assert Arrays.equals(entry.content, "c".bytes)
        }
        0 * visitor._
    }

    def "visits files of zip"() {
        def visitor = Mock(ClasspathEntryVisitor)

        def dir = tmpDir.createDir("dir")
        dir.file("empty").createDir()
        dir.file("a.class").text = "a"
        dir.file("a/b/c.class").text = "c"
        dir.file("a/b/empty").createDir()

        def zip = tmpDir.file("classes.zip")
        dir.zipTo(zip)

        when:
        walker.visit(dir, visitor)

        then:
        1 * visitor.visit({ it.name == "a.class" }) >> { ClasspathEntryVisitor.Entry entry ->
            assert entry.path.toString() == "a.class"
            assert Arrays.equals(entry.content, "a".bytes)
        }
        1 * visitor.visit({ it.name == "a/b/c.class" }) >> { ClasspathEntryVisitor.Entry entry ->
            assert entry.path.toString() == "a/b/c.class"
            assert Arrays.equals(entry.content, "c".bytes)
        }
        0 * visitor._
    }
}
