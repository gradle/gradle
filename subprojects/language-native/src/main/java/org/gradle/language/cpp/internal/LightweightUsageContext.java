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

package org.gradle.language.cpp.internal;

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.component.UsageContext;

import java.util.Set;

public class LightweightUsageContext implements UsageContext {
    private final String name;
    private final Usage usage;
    private final AttributeContainer attributes;

    public LightweightUsageContext(String name, Usage usage, AttributeContainer attributes) {
        this.name = name;
        this.usage = usage;
        this.attributes = attributes;
    }

    @Override
    public Usage getUsage() {
        return usage;
    }

    @Override
    public Set<? extends PublishArtifact> getArtifacts() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<? extends ModuleDependency> getDependencies() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<? extends DependencyConstraint> getDependencyConstraints() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }
}
