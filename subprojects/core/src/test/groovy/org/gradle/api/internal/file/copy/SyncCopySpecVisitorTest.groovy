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
package org.gradle.api.internal.file.copy

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.BaseDirFileResolver
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.WorkspaceTest
import org.jmock.Expectations
import org.junit.Test

import java.lang.reflect.Field

import static org.junit.Assert.assertTrue

class SyncCopySpecVisitorTest extends WorkspaceTest {

    FileCopyAction visitor

    def setup() {
        visitor = new FileCopyAction(new DirectInstantiator(), new BaseDirFileResolver(testDirectory))
    }

    void deletesExtraFilesFromDestinationDirectoryAtTheEndOfVisit() {
        given:
        file("src").with {
            createFile("subdir/included.txt")
            createFile("included.txt")
        }

        file("dest").with {
            createFile("subdir/included.txt")
            createFile("subdir/extra.txt")
            createFile("included.txt")
            createFile("extra.txt")
        }

        when:
        def result = visitor.with {
            copySpec.with {
                from "src"
                into "dest"
            }
            sync()
        }

        then:
        result.didWork
        file("dest").assertHasDescendants("subdir/included.txt", "included.txt");
    }

    @Test
    public void deletesExtraDirectoriesFromDestinationDirectoryAtTheEndOfVisit() throws Exception {
        TestFile destDir = tmpDir.createDir("dest");
        destDir.createFile("included.txt");
        destDir.createFile("extra/extra.txt");

        visitor(destDir);
        visitor.startVisit();
        visitor.visit(file("included.txt"));

        // TODO - delete these
        Field field = SyncCopySpecContentVisitor.class.getDeclaredField("visited");
        field.setAccessible(true);
        Set visited = (Set) field.get(visitor);
        assert visited.contains(new RelativePath(true, "included.txt"));
        assert !visited.contains(new RelativePath(true, "extra", "extra.txt"));
        final Set<RelativePath> actual = new HashSet<RelativePath>();
        new DirectoryFileTree(destDir).postfix().visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                actual.add(fileDetails.getRelativePath());
            }
        });
        assert actual.contains(new RelativePath(true, "included.txt"));
        assert actual.contains(new RelativePath(true, "extra", "extra.txt"));

        visitor.endVisit();

        destDir.assertHasDescendants("included.txt");
    }

    @Test
    public void doesNotDeleteDestDirectoryWhenNothingCopied() {
        TestFile destDir = tmpDir.createDir("dest");
        destDir.createFile("extra.txt");
        destDir.createFile("extra/extra.txt");

        visitor(destDir);
        visitor.startVisit();
        visitor.endVisit();

        destDir.assertHasDescendants();
    }

    @Test
    public void didWorkWhenDelegateDidWork() {
        visitor(tmpDir.createDir("test"));
        context.checking(new Expectations() {
            {
                allowing(delegate).getDidWork();
                will(returnValue(true));
            }
        });

        assertTrue(visitor.getDidWork());
    }

    @Test
    public void didWorkWhenFilesDeleted() {
        TestFile destDir = tmpDir.createDir("dest");
        destDir.createFile("extra.txt");

        visitor(destDir);
        visitor.startVisit();
        visitor.endVisit();

        assertTrue(visitor.getDidWork());
    }

}
