/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.profile;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An operation made up of other operations of type T.
 */
public class CompositeOperation<T extends Operation> extends Operation implements Iterable<T> {
    private List<T> children = new ArrayList<T>();

    public CompositeOperation(Iterable<? extends T> children) {
        this.children = Lists.newArrayList(children);
    }

    public List<T> getOperations() {
        return children;
    }

    public Iterator<T> iterator() {
        return children.iterator();
    }

    @Override
    long getElapsedTime() {
        long sum = 0;
        for (T child : children) {
            sum += child.getElapsedTime();
        }
        return sum;
    }

    public String getDescription() {
        return "<composite operation>";
    }
}
