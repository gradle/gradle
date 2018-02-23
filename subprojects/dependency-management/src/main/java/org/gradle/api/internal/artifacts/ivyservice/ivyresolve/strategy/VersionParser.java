/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import com.google.common.primitives.Longs;
import org.gradle.api.Transformer;

import java.util.ArrayList;
import java.util.List;

public class VersionParser implements Transformer<Version, String> {
    public static final VersionParser INSTANCE = new VersionParser();

    public VersionParser() {
    }

    @Override
    public Version transform(String original) {
        List<String> parts = new ArrayList<String>();
        boolean digit = false;
        int startPart = 0;
        int pos = 0;
        int endBase = 0;
        int endBaseStr = 0;
        for (; pos < original.length(); pos++) {
            char ch = original.charAt(pos);
            if (ch == '.' || ch == '_' || ch == '-' || ch == '+') {
                parts.add(original.substring(startPart, pos));
                startPart = pos + 1;
                digit = false;
                if (ch != '.' && endBaseStr == 0) {
                    endBase = parts.size();
                    endBaseStr = pos;
                }
            } else if (ch >= '0' && ch <= '9') {
                if (!digit && pos > startPart) {
                    if (endBaseStr == 0) {
                        endBase = parts.size() + 1;
                        endBaseStr = pos;
                    }
                    parts.add(original.substring(startPart, pos));
                    startPart = pos;
                }
                digit = true;
            } else {
                if (digit) {
                    if (endBaseStr == 0) {
                        endBase = parts.size() + 1;
                        endBaseStr = pos;
                    }
                    parts.add(original.substring(startPart, pos));
                    startPart = pos;
                }
                digit = false;
            }
        }
        if (pos > startPart) {
            parts.add(original.substring(startPart, pos));
        }
        DefaultVersion base = null;
        if (endBaseStr > 0) {
            base = new DefaultVersion(original.substring(0, endBaseStr), parts.subList(0, endBase), null);
        }
        return new DefaultVersion(original, parts, base);
    }

    private static class DefaultVersion implements Version {
        private final String source;
        private final String[] parts;
        private final Long[] numericParts;
        private final DefaultVersion baseVersion;

        public DefaultVersion(String source, List<String> parts, DefaultVersion baseVersion) {
            this.source = source;
            this.parts = parts.toArray(new String[0]);
            this.numericParts = new Long[this.parts.length];
            for (int i = 0; i < parts.size(); i++) {
                this.numericParts[i] = Longs.tryParse(this.parts[i]);
            }
            this.baseVersion = baseVersion == null ? this : baseVersion;
        }

        @Override
        public String toString() {
            return source;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            DefaultVersion other = (DefaultVersion) obj;
            return source.equals(other.source);
        }

        @Override
        public int hashCode() {
            return source.hashCode();
        }

        @Override
        public boolean isQualified() {
            return baseVersion != this;
        }

        @Override
        public Version getBaseVersion() {
            return baseVersion;
        }

        public String[] getParts() {
            return parts;
        }

        @Override
        public Long[] getNumericParts() {
            return numericParts;
        }

        @Override
        public String getSource() {
            return source;
        }
    }
}
