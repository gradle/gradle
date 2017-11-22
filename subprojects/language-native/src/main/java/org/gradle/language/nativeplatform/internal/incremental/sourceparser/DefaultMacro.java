/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;

public class DefaultMacro implements Macro {
    private final String name;
    private final IncludeType includeType;
    private final String value;

    public DefaultMacro(String name, IncludeType includeType, String value) {
        this.name = name;
        this.includeType = includeType;
        this.value = value;
    }

    @Override
    public String toString() {
        return "{" + name + "->" + value + "}";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public IncludeType getType() {
        return includeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        DefaultMacro other = (DefaultMacro) obj;
        return name.equals(other.name) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ value.hashCode();
    }
}
