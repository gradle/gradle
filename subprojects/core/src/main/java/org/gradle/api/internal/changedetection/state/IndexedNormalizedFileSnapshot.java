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

    public IndexedNormalizedFileSnapshot(String absolutePath, int index, FileContentSnapshot snapshot) {
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

    @Override
    protected boolean hasSameNormalizedPathAs(AbstractNormalizedFileSnapshot other) {
        if (other instanceof IndexedNormalizedFileSnapshot) {
            IndexedNormalizedFileSnapshot that = (IndexedNormalizedFileSnapshot) other;
            int myLength = absolutePath.length() - index;
            int otherLength = that.absolutePath.length() - that.index;
            if (myLength != otherLength) {
                return false;
            }
            return absolutePath.regionMatches(index, that.absolutePath, that.index, myLength);
        } else {
            return super.hasSameNormalizedPathAs(other);
        }
    }

    @Override
    protected int hashNormalizedPath() {
        int h = 0;
        for (int i = index; i < absolutePath.length(); i++) {
            h = 31 * h + absolutePath.charAt(i);
        }
        return h;
    }

    @Override
    protected int compareNormalizedPathTo(NormalizedFileSnapshot other) {
        if (other instanceof IndexedNormalizedFileSnapshot) {
            IndexedNormalizedFileSnapshot that = (IndexedNormalizedFileSnapshot) other;
            int length1 = absolutePath.length() - index;
            int length2 = that.absolutePath.length() - that.index;
            int limit = Math.min(length1, length2);

            for (int pos = 0; pos < limit; pos++) {
                char c1 = absolutePath.charAt(pos + index);
                char c2 = that.absolutePath.charAt(pos + that.index);
                if (c1 != c2) {
                    return c1 - c2;
                }
            }
            return length1 - length2;
        } else {
            return super.compareNormalizedPathTo(other);
        }
    }
}
