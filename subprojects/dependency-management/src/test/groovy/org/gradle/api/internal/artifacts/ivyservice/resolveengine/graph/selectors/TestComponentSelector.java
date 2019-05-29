/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.List;

public class TestComponentSelector implements ComponentSelector {
    @Override
    public String getDisplayName() {
        return "test";
    }

    @Override
    public boolean matchesStrictly(ComponentIdentifier identifier) {
        return false;
    }

    @Override
    public AttributeContainer getAttributes() {
        return ImmutableAttributes.EMPTY;
    }

    @Override
    public List<Capability> getRequestedCapabilities() {
        return ImmutableList.of();
    }
}
