/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.component.external.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class DefaultImmutableCapability implements Capability {
    private final String name;
    private final ImmutableList<String> providedBy;
    private final String prefer;

    public DefaultImmutableCapability(String name, ImmutableList<String> providedBy, String prefer) {
        this.name = name;
        this.providedBy = providedBy;
        this.prefer = prefer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultImmutableCapability that = (DefaultImmutableCapability) o;
        return Objects.equal(name, that.name)
            && Objects.equal(providedBy, that.providedBy)
            && Objects.equal(prefer, that.prefer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, providedBy, prefer);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getProvidedBy() {
        return providedBy;
    }

    @Override
    public String getPrefer() {
        return prefer;
    }

    @Override
    public String toString() {
        return "Capability '" + name + "' provided by " + providedBy + (prefer == null ? "" : " prefers " + prefer);
    }
}
