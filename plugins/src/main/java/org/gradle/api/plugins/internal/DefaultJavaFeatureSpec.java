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

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.plugins.jvm.internal.JvmModelingServices;
import org.gradle.api.plugins.jvm.internal.JvmVariantBuilderInternal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.ImmutableCapability;

import java.util.List;

public class DefaultJavaFeatureSpec implements FeatureSpecInternal {
    private final String name;
    private final List<Capability> capabilities = Lists.newArrayListWithExpectedSize(2);
    private final JvmModelingServices jvmEcosystemUtilities;

    private boolean overrideDefaultCapability = true;
    private SourceSet sourceSet;
    private boolean withJavadocJar = false;
    private boolean withSourcesJar = false;
    private boolean allowPublication = true;

    public DefaultJavaFeatureSpec(String name,
                                  Capability defaultCapability,
                                  JvmModelingServices jvmModelingServices) {
        this.name = name;
        this.jvmEcosystemUtilities = jvmModelingServices;
        this.capabilities.add(defaultCapability);
    }

    @Override
    public void usingSourceSet(SourceSet sourceSet) {
        this.sourceSet = sourceSet;
    }

    @Override
    public void capability(String group, String name, String version) {
        if (overrideDefaultCapability) {
            capabilities.clear();
            overrideDefaultCapability = false;
        }
        capabilities.add(new ImmutableCapability(group, name, version));
    }

    @Override
    public void create() {
        setupConfigurations(sourceSet);
    }

    @Override
    public void withJavadocJar() {
        withJavadocJar = true;
    }

    @Override
    public void withSourcesJar() {
        withSourcesJar = true;
    }

    @Override
    public void disablePublication() {
        allowPublication = false;
    }

    private void setupConfigurations(SourceSet sourceSet) {
        if (sourceSet == null) {
            throw new InvalidUserCodeException("You must specify which source set to use for feature '" + name + "'");
        }

        jvmEcosystemUtilities.createJvmVariant(name, builder -> {
            builder.usingSourceSet(sourceSet)
                .withDisplayName("feature " + name)
                .exposesApi();
            if (withJavadocJar) {
                builder.withJavadocJar();
            }
            if (withSourcesJar) {
                builder.withSourcesJar();
            }
            if (allowPublication) {
                builder.published();
            }
            for (Capability capability : capabilities) {
                ((JvmVariantBuilderInternal)builder).capability(capability);
            }
        });

    }

}
