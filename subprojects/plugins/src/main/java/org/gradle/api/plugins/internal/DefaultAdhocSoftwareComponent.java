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
package org.gradle.api.plugins.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.java.usagecontext.FeatureMapping;
import org.gradle.api.specs.Spec;

import java.util.List;
import java.util.Set;

public class DefaultAdhocSoftwareComponent implements AdhocComponentWithVariants, SoftwareComponentInternal {
    private final String componentName;
    private final List<FeatureMapping> variants = Lists.newArrayListWithExpectedSize(4);

    public DefaultAdhocSoftwareComponent(String componentName) {
        this.componentName = componentName;
    }

    @Override
    public String getName() {
        return componentName;
    }

    @Override
    public void addVariantsFromConfiguration(Configuration outgoingConfiguration, Spec<? super ConfigurationVariant> spec) {
        variants.add(new FeatureMapping(outgoingConfiguration, spec));
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        ImmutableSet.Builder<UsageContext> builder = new ImmutableSet.Builder<UsageContext>();
        for (FeatureMapping variant : variants) {
            variant.collectUsageContexts(builder);
        }
        return builder.build();
    }

}
