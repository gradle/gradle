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

package org.gradle.plugin.management.internal;

import com.google.common.collect.Iterators;

import java.util.Iterator;

public class MergedPluginRequests implements PluginRequests {

    private final PluginRequests first;
    private final PluginRequests second;

    public MergedPluginRequests(PluginRequests first, PluginRequests second) {
        if (first.isEmpty() || second.isEmpty()) {
            throw new IllegalStateException("requests must not be empty");
        }
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Iterator<PluginRequestInternal> iterator() {
        return Iterators.concat(first.iterator(), second.iterator());
    }
}
