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

import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.DisambiguationRule;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.attributes.MultipleCandidatesResult;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
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
@ServiceScope(Scope.Global.class)
public class PreferJavaRuntimeVariant extends EmptySchema {
    private static final Set<Attribute<?>> SUPPORTED_ATTRIBUTES = Sets.newHashSet(Usage.USAGE_ATTRIBUTE, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
    private final PreferRuntimeVariantUsageDisambiguationRule usageDisambiguationRule;
    private final PreferJarVariantUsageDisambiguationRule formatDisambiguationRule;

    public PreferJavaRuntimeVariant(NamedObjectInstantiator instantiator) {
        Usage runtimeUsage = instantiator.named(Usage.class, Usage.JAVA_RUNTIME);
        LibraryElements jarLibraryElements = instantiator.named(LibraryElements.class, LibraryElements.JAR);
        usageDisambiguationRule = new PreferRuntimeVariantUsageDisambiguationRule(runtimeUsage);
        formatDisambiguationRule = new PreferJarVariantUsageDisambiguationRule(jarLibraryElements);
    }

    @Override
    public Set<Attribute<?>> getAttributes() {
        return SUPPORTED_ATTRIBUTES;
    }

    @Nullable
    @Override
    public Attribute<?> getAttributeByName(String name) {
        for (Attribute<?> attr : SUPPORTED_ATTRIBUTES) {
            if (name.equals(attr.getName())) {
                return attr;
            }
        }
        return null;
    }

    @Override
    public DisambiguationRule<Object> disambiguationRules(Attribute<?> attribute) {
        if (Usage.USAGE_ATTRIBUTE.equals(attribute)) {
            return Cast.uncheckedCast(usageDisambiguationRule);
        }
        if (LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.equals(attribute)) {
            return Cast.uncheckedCast(formatDisambiguationRule);
        }
        return super.disambiguationRules(attribute);
    }

    private static class PreferRuntimeVariantUsageDisambiguationRule implements DisambiguationRule<Usage> {
        private final Usage runtimeUsage;

        public PreferRuntimeVariantUsageDisambiguationRule(Usage runtimeUsage) {
            this.runtimeUsage = runtimeUsage;
        }

        @Override
        public boolean doesSomething() {
            return true;
        }

        @Override
        public void execute(MultipleCandidatesResult<Usage> details) {
            if (details.getConsumerValue() == null) {
                Set<Usage> candidates = details.getCandidateValues();
                if (candidates.contains(runtimeUsage)) {
                    details.closestMatch(runtimeUsage);
                }
            }
        }
    }

    private static class PreferJarVariantUsageDisambiguationRule implements DisambiguationRule<LibraryElements> {
        private final LibraryElements jarLibraryElements;

        public PreferJarVariantUsageDisambiguationRule(LibraryElements jarLibraryElements) {
            this.jarLibraryElements = jarLibraryElements;
        }

        @Override
        public boolean doesSomething() {
            return true;
        }

        @Override
        public void execute(MultipleCandidatesResult<LibraryElements> details) {
            if (details.getConsumerValue() == null) {
                Set<LibraryElements> candidates = details.getCandidateValues();
                if (candidates.contains(jarLibraryElements)) {
                    details.closestMatch(jarLibraryElements);
                }
            }
        }
    }
}
