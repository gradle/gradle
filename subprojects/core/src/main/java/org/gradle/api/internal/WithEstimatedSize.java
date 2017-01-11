/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

public interface WithEstimatedSize {
    /**
     * Returns an estimate of the size of this collection or iterator. The idea is that this information
     * can be used internally to make better default dimensions for temporary objects. Therefore, it is
     * intended to return a value which is greater or equal to the real size (pessimistic). The typical use
     * case is creating a hash set or hash map of a collection without knowing the number of elements it
     * will contain. With this method we can properly size it and avoid resizes. The reason we use an
     * estimate size instead of the real size is that sometimes the real size is too expensive to compute.
     * Typically in a filtered collection you would have to actually do the filtering before knowing the size,
     * so killing all possible optimizations. Instead, it should return the size of the collection it wraps,
     * so that we have an estimate of the number of elements it may contain. The closer to reality is the better,
     * of course.
     *
     * @return the estimate size of the object
     */
    int estimatedSize();

    class Estimates {
        public static <T> int estimateSizeOf(Collection<T> collection) {
            if (collection instanceof HashSet || collection instanceof ArrayList || collection instanceof LinkedList) {
                return collection.size();
            }
            if (collection instanceof WithEstimatedSize) {
                return ((WithEstimatedSize) collection).estimatedSize();
            }
            return 10; // we don't know if the underlying collection can return a size in constant time
        }
    }
}
