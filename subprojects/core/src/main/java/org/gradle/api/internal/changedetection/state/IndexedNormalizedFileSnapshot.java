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

package org.gradle.api.internal.changedetection.state;

public class IndexedNormalizedFileSnapshot extends AbstractNormalizedFileSnapshot {
    private final String absolutePath;
    private final int index;

    public IndexedNormalizedFileSnapshot(String absolutePath, int index, IncrementalFileSnapshot snapshot) {
        super(snapshot);
        this.absolutePath = absolutePath;
        this.index = index;
    }

    @Override
    public String getNormalizedPath() {
        return absolutePath.substring(index);
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public int getIndex() {
        return index;
    }
}
