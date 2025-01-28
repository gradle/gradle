/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.platform;

import com.google.common.base.Preconditions;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.NonNullApi;

/**
 * Represents details about the specific architecture of the current platform that Gradle is running on.
 *
 * <p>
 * Get the current instance by injecting {@link CurrentArchitectureSupplier}.
 * </p>
 *
 * <p>
 * The provided string constants should be used to check for specific architectures, by using an equality check against {@link #getName()}.
 * Architectures that are not recognized by Gradle may be represented by this interface.
 * </p>
 *
 * <p>
 * The presence of a specific architecture as a constant does not imply that Gradle has full support for it, but rather that it is recognized by Gradle
 * and can be used for operations such as toolchain provisioning. Other architectures may not work as expected.
 * </p>
 *
 * @since 7.6
 */
@NonNullApi
@Incubating
public final class Architecture implements Named {
    public static final String X86 = "x86";
    public static final String X86_64 = "x86-64";
    public static final String AARCH64 = "aarch64";

    // Note: this class is intended to transition to a `record` later, hence the already public constructor.
    private final String name;

    public Architecture(String name) {
        this.name = Preconditions.checkNotNull(name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Architecture '" + name + "'";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Architecture that = (Architecture) obj;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
