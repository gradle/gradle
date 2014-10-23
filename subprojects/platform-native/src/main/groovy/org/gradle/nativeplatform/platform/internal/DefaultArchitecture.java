/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.platform.internal;

import java.util.Arrays;
import java.util.List;

public class DefaultArchitecture implements ArchitectureInternal {
    // TODO:DAZ Not sure if we need aliases any more. We should perhaps just use a 'canonical' name, or have some way to compare 2 names for equivalence.
    private static final List<String> X86_ALIASES = Arrays.asList("x86", "i386");
    private static final List<String> X86_64_ALIASES = Arrays.asList("x86_64", "amd64", "x64", "x86-64");
    private static final List<String> ITANIUM_ALIASES = Arrays.asList("ia64", "ia-64");
    private static final List<String> ARM_32_ALIASES = Arrays.asList("arm", "armv7", "arm-v7", "arm32");

    private final String name;

    public DefaultArchitecture(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return String.format("architecture '%s'", name);
    }

    public boolean isI386() {
        return X86_ALIASES.contains(name.toLowerCase());
    }

    public boolean isAmd64() {
        return X86_64_ALIASES.contains(name.toLowerCase());
    }

    public boolean isIa64() {
        return ITANIUM_ALIASES.contains(name.toLowerCase());
    }

    public boolean isArm() {
        return ARM_32_ALIASES.contains(name.toLowerCase());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DefaultArchitecture other = (DefaultArchitecture) obj;
        return name.equals(other.name);
    }
}
