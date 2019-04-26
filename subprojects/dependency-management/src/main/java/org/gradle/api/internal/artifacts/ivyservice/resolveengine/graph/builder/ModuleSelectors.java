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

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class ModuleSelectors<T extends ResolvableSelectorState> implements Iterable<T> {

    private static final Iterator EMPTY_ITERATOR = new EmptyIterator();

    private int selectorsCount = 0;
    private T singleSelector;
    private List<T> selectors;
    private List<T> dynamicSelectors;
    private boolean deferSelection;

    public boolean checkDeferSelection() {
        if (deferSelection) {
            deferSelection = false;
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<T> iterator() {
        if (selectorsCount == 0) {
            return EMPTY_ITERATOR;
        } else if (selectorsCount == 1) {
            return Iterators.singletonIterator(singleSelector);
        } else {
            if (selectors != null && dynamicSelectors != null) {
                return Iterators.concat(selectors.iterator(), dynamicSelectors.iterator());
            } else if (selectors != null) {
                return selectors.iterator();
            } else {
                return dynamicSelectors.iterator();
            }
        }
    }

    public void add(T selector, boolean deferSelection) {
        assert !contains(selector) : "Inconsistent call to add: should only be done if the selector isn't in use";
        if (selectorsCount == 0) {
            singleSelector = selector;
            this.deferSelection = deferSelection;
        } else if (selectorsCount == 1) {
            addSelector(singleSelector);
            addSelector(selector);
            singleSelector = null;
        } else {
            addSelector(selector);
        }
        selectorsCount++;
    }

    private void addSelector(T selector) {
        if (isDynamicSelector(selector)) {
            addDynamicSelector(selector);
        } else {
            addSimpleSelector(selector);
        }
    }

    private boolean isDynamicSelector(T selector) {
        return selector.getVersionConstraint() != null && selector.getVersionConstraint().isDynamic();
    }

    private void addSimpleSelector(T selector) {
        if (selectors == null) {
            selectors = Lists.newArrayListWithExpectedSize(3);
        }
        selectors.add(selector);
    }

    private void addDynamicSelector(T selector) {
        if (dynamicSelectors == null) {
            dynamicSelectors = Lists.newArrayListWithExpectedSize(3);
        }
        dynamicSelectors.add(selector);
    }

    public boolean remove(T selector) {
        assert contains(selector) : "Inconsistent call to remove: should only be done if the selector is in use";
        boolean removed = false;
        if (selectorsCount == 0) {
            return false;
        } else if (selectorsCount == 1) {
            removed = singleSelector.equals(selector);
            if (removed) {
                singleSelector = null;
            }
        } else {
            if (isDynamicSelector(selector)) {
                if (dynamicSelectors != null) {
                    removed = dynamicSelectors.remove(selector);
                    if (dynamicSelectors.isEmpty()) {
                        dynamicSelectors = null;
                    }
                }
            } else {
                if (selectors != null) {
                    removed = selectors.remove(selector);
                    if (selectors.isEmpty()) {
                        selectors = null;
                    }
                }
            }
        }
        if (removed) {
            selectorsCount--;
            if (selectorsCount == 1) {
                if (selectors != null) {
                    singleSelector = selectors.get(0);
                    selectors = null;
                } else {
                    singleSelector = dynamicSelectors.get(0);
                    dynamicSelectors = null;
                }
            }
        }
        return removed;
    }

    public int size() {
        return selectorsCount;
    }

    public T first() {
        if (selectorsCount == 0) {
            return null;
        } else if (selectorsCount == 1) {
            return singleSelector;
        } else {
            if (selectors != null) {
                return selectors.get(0);
            } else {
                return dynamicSelectors.get(0);
            }
        }
    }

    // Only used for assertions
    private boolean contains(T selector) {
        if (selectorsCount == 0) {
            return false;
        } else if (selectorsCount == 1) {
            return singleSelector.equals(selector);
        } else {
            if (isDynamicSelector(selector)) {
                return dynamicSelectors != null && dynamicSelectors.contains(selector);
            } else {
                return selectors != null && selectors.contains(selector);
            }
        }
    }

    private static class EmptyIterator implements Iterator<Object> {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            throw new NoSuchElementException("Empty iterator has no elements");
        }
    }
}
