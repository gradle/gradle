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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.Lists;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState;

import java.util.Iterator;
import java.util.List;

public class ModuleSelectors<T extends ResolvableSelectorState> implements Iterable<T> {

    List<T> selectors = Lists.newArrayListWithExpectedSize(4);

    @Override
    public Iterator<T> iterator() {
        return selectors.iterator();
    }

    public boolean contains(T selector) {
        return selectors.contains(selector);
    }

    public void add(T selector) {
        selectors.add(selector);
    }

    public boolean remove(T selector) {
        return selectors.remove(selector);
    }

    public int size() {
        return selectors.size();
    }

    public T first() {
        return selectors.get(0);
    }
}
