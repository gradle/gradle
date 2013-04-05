/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.util.ChangeListener;

import java.util.Map;

class MapMergeChangeListener<K, V> implements ChangeListener<Map.Entry<K, V>> {
    private final ChangeListener<FileCollectionSnapshot.Merge> listener;
    private final Map<K, V> newSnapshots;

    public MapMergeChangeListener(ChangeListener<FileCollectionSnapshot.Merge> listener, Map<K, V> targetMap) {
        this.listener = listener;
        this.newSnapshots = targetMap;
    }

    public void added(Map.Entry<K, V> element) {
        DefaultMerge merge = new DefaultMerge();
        listener.added(merge);
        if (!merge.isIgnore()) {
            newSnapshots.put(element.getKey(), element.getValue());
        }
    }

    public void removed(Map.Entry<K, V> element) {
        DefaultMerge merge = new DefaultMerge();
        listener.removed(merge);
        if (!merge.isIgnore()) {
            newSnapshots.remove(element.getKey());
        }
    }

    public void changed(Map.Entry<K, V> element) {
        DefaultMerge merge = new DefaultMerge();
        listener.changed(merge);
        if (!merge.isIgnore()) {
            newSnapshots.put(element.getKey(), element.getValue());
        }
    }

    private static class DefaultMerge implements FileCollectionSnapshot.Merge {
        private boolean ignore;

        public boolean isIgnore() {
            return ignore;
        }

        public void ignore() {
            ignore = true;
        }
    }
}
