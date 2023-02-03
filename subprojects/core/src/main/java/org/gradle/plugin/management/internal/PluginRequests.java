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

package org.gradle.plugin.management.internal;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public interface PluginRequests extends Iterable<PluginRequestInternal> {

    PluginRequests EMPTY = new EmptyPluginRequests();

    boolean isEmpty();

    class EmptyPluginRequests implements PluginRequests {

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public PluginRequests mergeWith(PluginRequests requests) {
            return requests;
        }

        @Override
        public Iterator<PluginRequestInternal> iterator() {
            return Collections.emptyIterator();
        }
    }

    default PluginRequests mergeWith(PluginRequests requests) {
        if (isEmpty()) {
            return requests;
        } else if (requests.isEmpty()) {
            return this;
        } else {
            return new MergedPluginRequests(this, requests);
        }
    }

    static PluginRequests of(PluginRequestInternal request) {
        return new SingletonPluginRequests(request);
    }

    static PluginRequests of(List<PluginRequestInternal> list) {
        if (list.isEmpty()) {
            return EMPTY;
        } else if (list.size() == 1) {
            return new SingletonPluginRequests(list.get(0));
        } else {
            return new MultiPluginRequests(list);
        }
    }

}
