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
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FileCollectionMatchers {
    @Factory
    public static <T extends FileCollection> Matcher<T> sameCollection(final FileCollection expected) {
        return new BaseMatcher<T>() {
            @Override
            public boolean matches(Object o) {
                FileCollection actual = (FileCollection) o;
                List<? extends FileCollection> actualCollections = unpack(actual);
                List<? extends FileCollection> expectedCollections = unpack(expected);
                boolean equals = actualCollections.equals(expectedCollections);
                if (!equals) {
                    System.out.println("expected: " + expectedCollections);
                    System.out.println("actual: " + actualCollections);
                }
                return equals;
            }

            private List<? extends FileCollection> unpack(FileCollection expected) {
                if (expected instanceof UnionFileCollection) {
                    UnionFileCollection collection = (UnionFileCollection) expected;
                    return new ArrayList<FileCollection>(collection.getSources());
                }
                if (expected instanceof DefaultConfigurableFileCollection) {
                    DefaultConfigurableFileCollection collection = (DefaultConfigurableFileCollection) expected;
                    return new ArrayList<FileCollection>((Set) collection.getFrom());
                }
                if (expected instanceof CompositeFileCollection) {
                    CompositeFileCollection collection = (CompositeFileCollection) expected;
                    DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(TestFiles.resolver());
                    collection.visitContents(context);
                    return context.resolveAsFileCollections();
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
