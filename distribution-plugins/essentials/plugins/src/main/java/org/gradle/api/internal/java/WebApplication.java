/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.java;

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.plugins.internal.AbstractUsageContext;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

public class WebApplication implements SoftwareComponentInternal {
    private final UsageContext webArchiveUsage;
    private final PublishArtifact warArtifact;
    private final String variantName;

    @Inject
    public WebApplication(PublishArtifact warArtifact, String variantName, AttributeContainer attributes) {
        this.warArtifact = warArtifact;
        this.variantName = variantName;
        this.webArchiveUsage = new WebArchiveUsageContext(attributes);
    }

    @Override
    public String getName() {
        return "web";
    }

    @Override
    public Set<UsageContext> getUsages() {
        return Collections.singleton(webArchiveUsage);
    }

    private class WebArchiveUsageContext extends AbstractUsageContext {
        public WebArchiveUsageContext(AttributeContainer attributes) {
            super(((AttributeContainerInternal)attributes).asImmutable(), Collections.singleton(warArtifact));
        }

        @Override
        public String getName() {
            return variantName;
        }

        @Override
        public Set<ModuleDependency> getDependencies() {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends DependencyConstraint> getDependencyConstraints() {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends Capability> getCapabilities() {
            return Collections.emptySet();
        }

        @Override
        public Set<ExcludeRule> getGlobalExcludes() {
            return Collections.emptySet();
        }
    }
}
