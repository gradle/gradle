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

import java.util.List;

public class LargeChildMap<T> extends AbstractListChildMap<T> {

    public LargeChildMap(List<Entry<T>> children) {
        super(children);
    }

    @Override
    protected Entry<T> findEntryWithPrefix(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        int childIndex = findChildIndexWithCommonPrefix(relativePath, caseSensitivity);
        if (childIndex < 0) {
            return null;
        }
        Entry<T> entry = children.get(childIndex);
        return relativePath.hasPrefix(entry.getPath(), caseSensitivity)
            ? entry
            : null;
    }
}
