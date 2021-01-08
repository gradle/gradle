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

public class DefaultArchitecture implements ArchitectureInternal {
    private final String name;

    public DefaultArchitecture(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return "architecture '" + name + "'";
    }

    @Override
    public boolean isI386() {
        return Architectures.X86.isAlias(name);
    }

    @Override
    public boolean isAmd64() {
        return Architectures.X86_64.isAlias(name);
    }

    @Override
    public boolean isIa64() {
        return Architectures.IA_64.isAlias(name);
    }

    @Override
    public boolean isArm() {
        return Architectures.ARM_V7.isAlias(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultArchitecture other = (DefaultArchitecture) o;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
