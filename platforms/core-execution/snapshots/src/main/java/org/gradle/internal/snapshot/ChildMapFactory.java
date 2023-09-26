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

package org.gradle.internal.snapshot;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class ChildMapFactory {
    /**
     * If a node has fewer children, we use a linear search for the child.
     * We use this limit since {@link VfsRelativePath#compareToFirstSegment(String, CaseSensitivity)}
     * is about twice as slow as {@link VfsRelativePath#hasPrefix(String, CaseSensitivity)},
     * so comparing the searched path to all of the children is actually faster than doing a binary search.
     */
    private static final int MINIMUM_CHILD_COUNT_FOR_BINARY_SEARCH = 10;

    public static <T> ChildMap<T> childMap(CaseSensitivity caseSensitivity, Collection<ChildMap.Entry<T>> entries) {
        List<ChildMap.Entry<T>> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(Comparator.comparing(ChildMap.Entry::getPath, PathUtil.getPathComparator(caseSensitivity)));
        return childMapFromSorted(sortedEntries);
    }

    public static <T> ChildMap<T> childMapFromSorted(List<ChildMap.Entry<T>> sortedEntries) {
        int size = sortedEntries.size();
        switch (size) {
            case 0:
                return EmptyChildMap.getInstance();
            case 1:
                return new SingletonChildMap<>(sortedEntries.get(0));
            default:
                return (size < MINIMUM_CHILD_COUNT_FOR_BINARY_SEARCH)
                    ? new MediumChildMap<>(sortedEntries)
                    : new LargeChildMap<>(sortedEntries);
        }
    }

    static <T> ChildMap<T> childMap(CaseSensitivity caseSensitivity, ChildMap.Entry<T> entry1, ChildMap.Entry<T> entry2) {
        int compared = PathUtil.getPathComparator(caseSensitivity).compare(entry1.getPath(), entry2.getPath());
        List<ChildMap.Entry<T>> sortedEntries = compared < 0
            ? ImmutableList.of(entry1, entry2)
            : ImmutableList.of(entry2, entry1);
        return childMapFromSorted(sortedEntries);
    }
}
