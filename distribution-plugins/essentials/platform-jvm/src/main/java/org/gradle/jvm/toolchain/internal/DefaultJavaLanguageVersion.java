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

package org.gradle.jvm.toolchain.internal;

import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.io.Serializable;

public class DefaultJavaLanguageVersion implements JavaLanguageVersion, Serializable {

    static final int LOWER_CACHED_VERSION = 4;
    static final int HIGHER_CACHED_VERSION = 19;
    static final JavaLanguageVersion[] KNOWN_VERSIONS;

    static {
        KNOWN_VERSIONS = new JavaLanguageVersion[HIGHER_CACHED_VERSION - LOWER_CACHED_VERSION + 1];
        for (int version = LOWER_CACHED_VERSION; version <= HIGHER_CACHED_VERSION; version++) {
            KNOWN_VERSIONS[version - LOWER_CACHED_VERSION] = new DefaultJavaLanguageVersion(version);
        }
    }

    public static JavaLanguageVersion of(int version) {
        if (version <= 0) {
            throw new IllegalArgumentException("JavaLanguageVersion must be a positive integer, not " + version);
        }
        if (version >= LOWER_CACHED_VERSION && version <= HIGHER_CACHED_VERSION) {
            return KNOWN_VERSIONS[version - LOWER_CACHED_VERSION];
        } else {
            return new DefaultJavaLanguageVersion(version);
        }
    }

    private final int version;

    private DefaultJavaLanguageVersion(int version) {
        this.version = version;
    }

    @Override
    public int asInt() {
        return version;
    }

    @Override
    public String toString() {
        if (version < 5) {
            return String.format("1.%d", version);
        }
        return Integer.toString(version);
    }

    @Override
    public boolean canCompileOrRun(JavaLanguageVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(JavaLanguageVersion other) {
        return Integer.compare(version, other.asInt());
    }

    @Override
    public boolean canCompileOrRun(int otherVersion) {
        return version >= otherVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultJavaLanguageVersion that = (DefaultJavaLanguageVersion) o;
        return version == that.version;
    }

    @Override
    public int hashCode() {
        return version;
    }
}
