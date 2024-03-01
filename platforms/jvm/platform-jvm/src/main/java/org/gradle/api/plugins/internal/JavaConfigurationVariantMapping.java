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
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.publish.internal.component.ConfigurationVariantDetailsInternal;
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;
import java.util.Set;

public class JavaConfigurationVariantMapping implements Action<ConfigurationVariantDetails> {

    /**
     * A list of known artifact types which are known to prevent from
     * publication.
     */
    public static final Set<String> UNPUBLISHABLE_VARIANT_ARTIFACTS = ImmutableSet.of(
        ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
        ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY,
        ArtifactTypeDefinition.DIRECTORY_TYPE
    );

    private final String scope;
    private final boolean optional;
    private final Configuration resolutionConfiguration;

    public JavaConfigurationVariantMapping(String scope, boolean optional) {
        this(scope, optional, null);
    }

    public JavaConfigurationVariantMapping(String scope, boolean optional, @Nullable Configuration resolutionConfiguration) {
        this.scope = scope;
        this.optional = optional;
        this.resolutionConfiguration = resolutionConfiguration;
    }

    @Override
    public void execute(ConfigurationVariantDetails details) {
        ConfigurationVariant variant = details.getConfigurationVariant();
        if (UnpublishableArtifactTypeSpec.INSTANCE.isSatisfiedBy(variant)) {
            details.skip();
        } else {
            details.mapToMavenScope(scope);
            if (optional) {
                details.mapToOptional();
            }
            if (resolutionConfiguration != null) {
                ((ConfigurationVariantDetailsInternal) details).dependencyMapping(dependencyMapping -> {
                    dependencyMapping.fromResolutionOf(resolutionConfiguration);
                });
            }
        }
    }

    private static class UnpublishableArtifactTypeSpec implements Spec<ConfigurationVariant> {
        private static final UnpublishableArtifactTypeSpec INSTANCE = new UnpublishableArtifactTypeSpec();

        @Override
        public boolean isSatisfiedBy(ConfigurationVariant element) {
            for (PublishArtifact artifact : element.getArtifacts()) {
                if (UNPUBLISHABLE_VARIANT_ARTIFACTS.contains(artifact.getType())) {
                    return true;
                }
            }
            return false;
        }
    }
}
