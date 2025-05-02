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
package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.Set;

/**
 * When no consumer attributes are provided, prefer the Java runtime variant over the API variant.
 *
 * Gradle has long assumed that, by default, consumers of a maven repository require the _runtime_ variant
 * of the published library.
 * The following disambiguation rule encodes this assumption for the case where a java library is published
 * with variants using Gradle module metadata. This will allow us to migrate to consuming the new module
 * metadata format by default without breaking a bunch of consumers that depend on this assumption,
 * declaring no preference for a particular variant.
 */
@ServiceScope(Scope.BuildSession.class)
public class PreferJavaRuntimeVariant {

    private final ImmutableAttributesSchema instance;

    @Inject
    public PreferJavaRuntimeVariant(
        NamedObjectInstantiator instantiator,
        ImmutableAttributesSchemaFactory schemaFactory
    ) {
        this.instance = create(instantiator, schemaFactory);
    }

    public ImmutableAttributesSchema getSchema() {
        return instance;
    }

    private static ImmutableAttributesSchema create(
        NamedObjectInstantiator instantiator,
        ImmutableAttributesSchemaFactory schemaFactory
    ) {
        Usage runtimeUsage = instantiator.named(Usage.class, Usage.JAVA_RUNTIME);
        LibraryElements jarLibraryElements = instantiator.named(LibraryElements.class, LibraryElements.JAR);

        PreferRuntimeVariantUsageDisambiguationRule usageDisambiguationRule = new PreferRuntimeVariantUsageDisambiguationRule(runtimeUsage);
        PreferJarVariantUsageDisambiguationRule formatDisambiguationRule = new PreferJarVariantUsageDisambiguationRule(jarLibraryElements);

        ImmutableMap<Attribute<?>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<?>> strategies =
            ImmutableMap.of(
                Usage.USAGE_ATTRIBUTE,
                new ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<>(
                    ImmutableList.of(),
                    ImmutableList.of(usageDisambiguationRule)
                ),
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                new ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<>(
                    ImmutableList.of(),
                    ImmutableList.of(formatDisambiguationRule)
                )
            );

        return schemaFactory.create(
            strategies,
            ImmutableList.of(Usage.USAGE_ATTRIBUTE, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)
        );
    }

    private static class PreferRuntimeVariantUsageDisambiguationRule implements Action<MultipleCandidatesDetails<Usage>> {
        private final Usage runtimeUsage;

        public PreferRuntimeVariantUsageDisambiguationRule(Usage runtimeUsage) {
            this.runtimeUsage = runtimeUsage;
        }

        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            if (details.getConsumerValue() == null) {
                Set<Usage> candidates = details.getCandidateValues();
                if (candidates.contains(runtimeUsage)) {
                    details.closestMatch(runtimeUsage);
                }
            }
        }
    }

    private static class PreferJarVariantUsageDisambiguationRule implements Action<MultipleCandidatesDetails<LibraryElements>> {
        private final LibraryElements jarLibraryElements;

        public PreferJarVariantUsageDisambiguationRule(LibraryElements jarLibraryElements) {
            this.jarLibraryElements = jarLibraryElements;
        }

        @Override
        public void execute(MultipleCandidatesDetails<LibraryElements> details) {
            if (details.getConsumerValue() == null) {
                Set<LibraryElements> candidates = details.getCandidateValues();
                if (candidates.contains(jarLibraryElements)) {
                    details.closestMatch(jarLibraryElements);
                }
            }
        }
    }
}
