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
package org.gradle.api.internal.artifacts;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.DependencyPacking;
import org.gradle.api.internal.ReusableAction;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.Set;

public abstract class JavaEcosystemSupport {
    public static void configureSchema(AttributesSchema attributesSchema, final ObjectFactory objectFactory) {
        AttributeMatchingStrategy<Usage> usageSchema = attributesSchema.attribute(Usage.USAGE_ATTRIBUTE);
        usageSchema.getCompatibilityRules().add(UsageCompatibilityRules.class);
        usageSchema.getDisambiguationRules().add(UsageDisambiguationRules.class, new Action<ActionConfiguration>() {
            @Override
            public void execute(ActionConfiguration actionConfiguration) {
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API_JARS));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API_CLASSES));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_JARS));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_CLASSES));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_RESOURCES));
            }
        });
        AttributeMatchingStrategy<DependencyPacking> packingSchema = attributesSchema.attribute(DependencyPacking.PACKING);
        packingSchema.getCompatibilityRules().add(PackingCompatibilityRules.class);
        packingSchema.getDisambiguationRules().add(PackingDisambiguationRules.class);
    }

    @VisibleForTesting
    public static class UsageDisambiguationRules implements AttributeDisambiguationRule<Usage>, ReusableAction {
        final Usage javaApi;
        final Usage javaRuntime;
        final Usage javaApiJars;
        final Usage javaApiClasses;
        final Usage javaRuntimeJars;
        final Usage javaRuntimeClasses;
        final Usage javaRuntimeResources;

        final ImmutableSet<Usage> apiVariants;
        final ImmutableSet<Usage> runtimeVariants;

        @Inject
        UsageDisambiguationRules(Usage javaApi,
                                 Usage javaApiJars,
                                 Usage javaApiClasses,
                                 Usage javaRuntime,
                                 Usage javaRuntimeJars,
                                 Usage javaRuntimeClasses,
                                 Usage javaRuntimeResources) {
            this.javaApi = javaApi;
            this.javaApiJars = javaApiJars;
            this.javaApiClasses = javaApiClasses;
            this.apiVariants = ImmutableSet.of(javaApi, javaApiJars, javaApiClasses);
            this.javaRuntime = javaRuntime;
            this.javaRuntimeJars = javaRuntimeJars;
            this.javaRuntimeClasses = javaRuntimeClasses;
            this.javaRuntimeResources = javaRuntimeResources;
            this.runtimeVariants = ImmutableSet.of(javaRuntime, javaRuntimeJars, javaRuntimeClasses, javaRuntimeResources);
        }

        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            Set<Usage> candidateValues = details.getCandidateValues();
            Usage consumerValue = details.getConsumerValue();
            if (consumerValue == null) {
                if (candidateValues.contains(javaRuntimeJars)) {
                    // Use the Jars when nothing has been requested
                    details.closestMatch(javaRuntimeJars);
                } else if (candidateValues.contains(javaRuntime)) {
                    // Use the runtime when nothing has been requested
                    details.closestMatch(javaRuntime);
                }
            } else {
                if (candidateValues.contains(consumerValue)) {
                    details.closestMatch(consumerValue);
                } else if (apiVariants.contains(consumerValue)) {
                    // we're asking for an API variant, but no exact match was found
                    if (candidateValues.contains(javaApiClasses)) {
                        // prefer the most lightweight API
                        details.closestMatch(javaApiClasses);
                    } else if (candidateValues.contains(javaApiJars)) {
                        details.closestMatch(javaApiJars);
                    } else if (candidateValues.contains(javaApi)) {
                        // Prefer the API over the runtime when the API has been requested
                        details.closestMatch(javaApi);
                    }
                } else if (runtimeVariants.contains(consumerValue)) {
                    // we're asking for a runtime variant, but no exact match was found
                    if (candidateValues.contains(javaRuntimeJars)) {
                        details.closestMatch(javaRuntimeJars);
                    } else if (candidateValues.contains(javaRuntime)) {
                        details.closestMatch(javaRuntime);
                    }
                }
            }
        }
    }

    @VisibleForTesting
    public static class UsageCompatibilityRules implements AttributeCompatibilityRule<Usage>, ReusableAction {
        private static final Set<String> COMPATIBLE_WITH_JAVA_API = ImmutableSet.of(
                Usage.JAVA_API_JARS,
                Usage.JAVA_API_CLASSES,
                Usage.JAVA_RUNTIME_JARS,
                Usage.JAVA_RUNTIME_CLASSES,
                Usage.JAVA_RUNTIME
        );
        private static final Set<String> COMPATIBLE_WITH_JAVA_API_JARS = ImmutableSet.of(
                Usage.JAVA_API,
                Usage.JAVA_RUNTIME_JARS,
                Usage.JAVA_RUNTIME
        );
        private static final Set<String> COMPATIBLE_WITH_JAVA_API_CLASSES = ImmutableSet.of(
                Usage.JAVA_API,
                Usage.JAVA_API_JARS,
                Usage.JAVA_RUNTIME_JARS,
                Usage.JAVA_RUNTIME_CLASSES,
                Usage.JAVA_RUNTIME
        );
        private static final Set<String> COMPATIBLE_WITH_JAVA_RUNTIME_CLASSES = ImmutableSet.of(
                Usage.JAVA_RUNTIME,
                Usage.JAVA_RUNTIME_JARS
        );
        private static final Set<String> COMPATIBLE_WITH_JAVA_RUNTIME_RESOURCES = ImmutableSet.of(
                Usage.JAVA_RUNTIME,
                Usage.JAVA_RUNTIME_JARS
        );

        @Override
        public void execute(CompatibilityCheckDetails<Usage> details) {
            String consumerValue = details.getConsumerValue().getName();
            String producerValue = details.getProducerValue().getName();
            if (consumerValue.equals(Usage.JAVA_API)) {
                if (COMPATIBLE_WITH_JAVA_API.contains(producerValue)) {
                    details.compatible();
                }
                return;
            }
            if (consumerValue.equals(Usage.JAVA_API_CLASSES)) {
                if (COMPATIBLE_WITH_JAVA_API_CLASSES.contains(producerValue)) {
                    details.compatible();
                }
                return;
            }
            if (consumerValue.equals(Usage.JAVA_API_JARS)) {
                if (COMPATIBLE_WITH_JAVA_API_JARS.contains(producerValue)) {
                    details.compatible();
                }
                return;
            }
            if (consumerValue.equals(Usage.JAVA_RUNTIME) && producerValue.equals(Usage.JAVA_RUNTIME_JARS)) {
                details.compatible();
                return;
            }
            if (consumerValue.equals(Usage.JAVA_RUNTIME_CLASSES)) {
                if (COMPATIBLE_WITH_JAVA_RUNTIME_CLASSES.contains(producerValue)) {
                    // Can use the Java runtime jars if present, but prefer Java runtime classes
                    details.compatible();
                    return;
                }
            }
            if (consumerValue.equals(Usage.JAVA_RUNTIME_RESOURCES)) {
                if (COMPATIBLE_WITH_JAVA_RUNTIME_RESOURCES.contains(producerValue)) {
                    // Can use the Java runtime jars if present, but prefer Java runtime resources
                    details.compatible();
                    return;
                }
            }
            if (consumerValue.equals(Usage.JAVA_RUNTIME_JARS) && producerValue.equals(Usage.JAVA_RUNTIME)) {
                // Can use the Java runtime if present, but prefer Java runtime jar
                details.compatible();
            }
        }
    }

    @VisibleForTesting
    public static class PackingCompatibilityRules implements AttributeCompatibilityRule<DependencyPacking>, ReusableAction {
        private static final Set<String> COMPATIBLE_WITH_EXTERNAL = ImmutableSet.of(
                // if we ask for "external" dependencies, it's still fine to bring a fat jar if nothing else is available
                DependencyPacking.FATJAR,
                DependencyPacking.SHADOWED
        );

        @Override
        public void execute(CompatibilityCheckDetails<DependencyPacking> details) {
            DependencyPacking consumerValue = details.getConsumerValue();
            DependencyPacking producerValue = details.getProducerValue();
            if (consumerValue == null) {
                // consumer didn't express any preference, everything fits
                details.compatible();
                return;
            }
            String consumerValueName = consumerValue.getName();
            String producerValueName = producerValue.getName();
            if (DependencyPacking.EXTERNAL.equals(consumerValueName)) {
                if (COMPATIBLE_WITH_EXTERNAL.contains(producerValueName)) {
                    details.compatible();
                }
            } else if (DependencyPacking.FATJAR.equals(consumerValueName)) {
                // asking for a fat jar. If everything available is a shadow jar, that's fine
                if (DependencyPacking.SHADOWED.equals(producerValueName)) {
                    details.compatible();
                }
            }
        }
    }

    @VisibleForTesting
    public static class PackingDisambiguationRules implements AttributeDisambiguationRule<DependencyPacking>, ReusableAction {

        @Override
        public void execute(MultipleCandidatesDetails<DependencyPacking> details) {
            DependencyPacking consumerValue = details.getConsumerValue();
            Set<DependencyPacking> candidateValues = details.getCandidateValues();
            if (candidateValues.contains(consumerValue)) {
                details.closestMatch(consumerValue);
                return;
            }
            if (consumerValue == null) {
                DependencyPacking fatJar = null;
                for (DependencyPacking candidateValue : candidateValues) {
                    if (DependencyPacking.EXTERNAL.equals(candidateValue.getName())) {
                        details.closestMatch(candidateValue);
                        return;
                    } else if (DependencyPacking.FATJAR.equals(candidateValue.getName())) {
                        fatJar = candidateValue;
                    }
                }
                if (fatJar != null) {
                    details.closestMatch(fatJar);
                }
            } else {
                String consumerValueName = consumerValue.getName();
                if (DependencyPacking.EXTERNAL.equals(consumerValueName)) {
                    for (DependencyPacking candidateValue : candidateValues) {
                        if (DependencyPacking.FATJAR.equals(candidateValue.getName())) {
                            details.closestMatch(candidateValue);
                            return;
                        }
                    }
                }
            }
        }
    }
}
