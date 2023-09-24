/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.process.internal.health.memory;

import org.gradle.api.NonNullApi;

import java.util.Arrays;

@NonNullApi
public class DefaultAvailableOsMemoryStatusAspect implements OsMemoryStatusAspect.Available {
    private final String name;
    private final long total;
    private final long free;

    public DefaultAvailableOsMemoryStatusAspect(String name, long total, long free) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        if (free < 0) {
            throw new IllegalArgumentException("free must be >= 0");
        }
        this.name = name;
        this.total = total;
        this.free = free;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getTotal() {
        return total;
    }

    @Override
    public long getFree() {
        return free;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultAvailableOsMemoryStatusAspect that = (DefaultAvailableOsMemoryStatusAspect) o;
        return total == that.total && free == that.free && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{name, total, free});
    }

    @Override
    public String toString() {
        return "AvailableMemory[" + name + ", total=" + total + ", free=" + free + ']';
    }
}
