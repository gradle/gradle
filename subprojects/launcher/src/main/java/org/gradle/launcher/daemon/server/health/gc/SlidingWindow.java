/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.server.health.gc;

import java.util.Set;

public interface SlidingWindow<T> {
    /**
     * Maintains a fixed size window of elements by sliding the window one element and inserting a new element at the end.
     *
     * @param element The element to add
     */
    public void slideAndInsert(T element);

    /**
     * Returns a snapshot of the elements in the window as a Set view.
     *
     * @return Set view of the elements
     */
    public Set<T> snapshot();
}
