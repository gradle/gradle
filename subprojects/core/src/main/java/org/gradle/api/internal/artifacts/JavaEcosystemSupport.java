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
import org.gradle.api.ecosystem.Ecosystem;
import org.gradle.api.internal.ReusableAction;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.component.ImmutableEcosystem;

import javax.inject.Inject;
import java.util.Set;

public abstract class JavaEcosystemSupport {
    private static final Ecosystem JAVA_ECOSYSTEM = new ImmutableEcosystem(
            "org.gradle.ecosystem.java",
            "The JVM ecosystem providing support for the usage and dependency packing attributes"
    );

    public static void configureSchema(AttributesSchema attributesSchema, final ObjectFactory objectFactory) {
        AttributeMatchingStrategy<Usage> matchingStrategy = attributesSchema.attribute(Usage.USAGE_ATTRIBUTE);
        matchingStrategy.getCompatibilityRules().add(UsageCompatibilityRules.class);
        matchingStrategy.getDisambiguationRules().add(UsageDisambiguationRules.class, new Action<ActionConfiguration>() {
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
        attributesSchema.registerEcosystem(JAVA_ECOSYSTEM.getName(), JAVA_ECOSYSTEM.getDescription());
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
}
