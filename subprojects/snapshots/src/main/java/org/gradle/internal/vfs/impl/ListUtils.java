/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import java.util.Comparator;
import java.util.List;

public abstract class ListUtils {

    /**
     * Does a binary search for an element determined by the return value of a comparator.
     *
     * See {@link java.util.Collections#binarySearch(List, Object, Comparator)}.
     * @param sortedElements {@link java.util.RandomAccess} list, sorted compatible with the comparator
     * @param searchForComparator determines which element to search for.
     * @return the index of the search key, if it is contained in the list;
     *         otherwise, <code>(-(<i>insertion point</i>) - 1)</code>.  The
     *         <i>insertion point</i> is defined as the point at which the
     *         key would be inserted into the list: the index of the first
     *         element greater than the key, or {@code list.size()} if all
     *         elements in the list are less than the specified key.  Note
     *         that this guarantees that the return value will be &gt;= 0 if
     *         and only if the key is found.
     */
    public static <T> int binarySearch(List<T> sortedElements, SearchForComparator<T> searchForComparator) {
        int low = 0;
        int high = sortedElements.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = sortedElements.get(mid);
            int cmp = searchForComparator.compare(midVal);

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found
    }

    interface SearchForComparator<T> {
        /**
         * Compares what is searched for to a candidate.
         *
         * Returns a negative integer, zero, or a positive integer if the candidate is less than, equal to, or greater than the searched for value.
         *
         * If there would be an element to search for, then this method is equivalent to
         * {@code candidate.compareTo(elementToSearchFor)}
         */
        int compare(T candidate);
    }

}
