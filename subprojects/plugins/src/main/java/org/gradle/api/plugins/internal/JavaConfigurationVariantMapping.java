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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Category;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;

import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.plugins.JavaBasePlugin.UNPUBLISHABLE_VARIANT_ARTIFACTS;

public class JavaConfigurationVariantMapping implements Action<ConfigurationVariantDetails> {
    private final String scope;
    private final boolean optional;
    private final JavaPluginConvention javaPluginConvention;

    public JavaConfigurationVariantMapping(String scope, boolean optional, @Nullable JavaPluginConvention javaPluginConvention) {
        this.scope = scope;
        this.optional = optional;
        this.javaPluginConvention = javaPluginConvention;
    }

    @Override
    public void execute(ConfigurationVariantDetails details) {
        ConfigurationVariant variant = details.getConfigurationVariant();
        if (isDocumentationPublishingDisabled() && isDocumentationVariant(variant)) {
            details.skip();
        } else if (UnpublishableArtifactTypeSpec.INSTANCE.isSatisfiedBy(variant)) {
            details.skip();
        } else {
            details.mapToMavenScope(scope);
            if (optional || isDocumentationVariant(variant)) {
                details.mapToOptional();
            }
        }
    }

    private boolean isDocumentationPublishingDisabled() {
        return javaPluginConvention == null || !javaPluginConvention.getPublishJavadocAndSources();
    }

    private boolean isDocumentationVariant(ConfigurationVariant variant) {
        Category category = variant.getAttributes().getAttribute(CATEGORY_ATTRIBUTE);
        return category != null && Category.DOCUMENTATION.equals(category.getName());
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
