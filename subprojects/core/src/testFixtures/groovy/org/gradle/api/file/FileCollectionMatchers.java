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

package org.gradle.api.file;

import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileCollectionMatchers {
    @Factory
    public static <T extends FileCollection> Matcher<T> sameCollection(final FileCollection expected) {
        return new BaseMatcher<T>() {
            @Override
            public boolean matches(Object o) {
                FileCollection actual = (FileCollection) o;
                List<Object> actualCollections = unpack(actual);
                List<Object> expectedCollections = unpack(expected);
                boolean equals = actualCollections.equals(expectedCollections);
                if (!equals) {
                    System.out.println("expected: " + expectedCollections);
                    System.out.println("actual: " + actualCollections);
                }
                return equals;
            }

            private List<Object> unpack(FileCollection expected) {
                if (expected instanceof FileCollectionInternal) {
                    FileCollectionInternal collection = (CompositeFileCollection) expected;
                    List<Object> collections = new ArrayList<>();
                    collection.visitStructure(new FileCollectionStructureVisitor() {
                        @Override
                        public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                            for (File content : contents) {
                                collections.add(content);
                            }
                        }

                        @Override
                        public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                            collections.add(fileTree);
                        }

                        @Override
                        public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                            collections.add(fileTree);
                        }
                    });
                    return collections;
                }
                throw new RuntimeException("Cannot get children of " + expected);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("same file collection as ").appendValue(expected);
            }
        };
    }
}
