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

import com.google.common.collect.ImmutableMap;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.io.Serializable;
import java.util.Map;

public class DefaultJavaLanguageVersion implements JavaLanguageVersion, Serializable {

    public static final Map<Integer, JavaLanguageVersion> KNOWN_VERSIONS;

    static {
        ImmutableMap.Builder<Integer, JavaLanguageVersion> builder = ImmutableMap.builder();
        for (int version = 4; version < 18; version++) {
            builder.put(version, new DefaultJavaLanguageVersion(version));
        }
        KNOWN_VERSIONS = builder.build();
    }

    private final int version;

    public DefaultJavaLanguageVersion(int version) {
        this.version = version;
    }
    @Override
    public int asInt() {
        return version;
    }

    @Override
    public String asString() {
        return Integer.toString(version);
    }

    @Override
    public boolean interoperatesWith(JavaLanguageVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(JavaLanguageVersion other) {
        return Integer.compare(version, other.asInt());
    }

    @Override
    public boolean interoperatesWith(int otherVersion) {
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

    @Override
    public String toString() {
        return String.valueOf(version);
    }
}
