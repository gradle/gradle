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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME;

/**
 * A SoftwareComponent representing a library that runs on a java virtual machine.
 */
public class JavaLibrary implements SoftwareComponentInternal {

    private final Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final UsageContext runtimeUsage;
    private final UsageContext compileUsage;
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;

    @Inject
    public JavaLibrary(ObjectFactory objectFactory, ConfigurationContainer configurations, ImmutableAttributesFactory attributesFactory, PublishArtifact artifact) {
        this.configurations = configurations;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.runtimeUsage = createRuntimeUsageContext();
        this.compileUsage = createCompileUsageContext();
        if (artifact != null) {
            this.artifacts.add(artifact);
        }
    }

    @VisibleForTesting
    Set<PublishArtifact> getArtifacts() {
        return artifacts;
    }

    public String getName() {
        return "java";
    }

    public Set<UsageContext> getUsages() {
        return ImmutableSet.of(runtimeUsage, compileUsage);
    }

    private abstract class AbstractUsageContext implements UsageContext {
        private final Usage usage;
        private final ImmutableAttributes attributes;
        AbstractUsageContext(String usageName) {
            this.usage = objectFactory.named(Usage.class, usageName);
            this.attributes = attributesFactory.of(Usage.USAGE_ATTRIBUTE, usage);
        }

        @Override
        public AttributeContainer getAttributes() {
            return attributes;
        }

        @Override
        public Usage getUsage() {
            return usage;
        }

        public Set<PublishArtifact> getArtifacts() {
            return artifacts;
        }
    }

    private UsageContext createRuntimeUsageContext() {
        return new ConfigurationUsageContext(Usage.JAVA_RUNTIME, "runtime", RUNTIME_ELEMENTS_CONFIGURATION_NAME);
    }

    private UsageContext createCompileUsageContext() {
        return new ConfigurationUsageContext(Usage.JAVA_API, "api", API_ELEMENTS_CONFIGURATION_NAME);
    }

    private class ConfigurationUsageContext extends AbstractUsageContext {
        private final String name;
        private final String configurationName;
        private DomainObjectSet<ModuleDependency> dependencies;
        private DomainObjectSet<DependencyConstraint> dependencyConstraints;
        private Set<? extends Capability> capabilities;
        private Set<ExcludeRule> excludeRules;


        ConfigurationUsageContext(String usageName, String name, String configurationName) {
            super(usageName);
            this.name = name;
            this.configurationName = configurationName;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<ModuleDependency> getDependencies() {
            if (dependencies == null) {
                dependencies = getConfiguration().getIncoming().getDependencies().withType(ModuleDependency.class);
            }
            return dependencies;
        }

        @Override
        public Set<? extends DependencyConstraint> getDependencyConstraints() {
            if (dependencyConstraints == null) {
                dependencyConstraints = getConfiguration().getIncoming().getDependencyConstraints();
            }
            return dependencyConstraints;
        }

        @Override
        public Set<? extends Capability> getCapabilities() {
            if (capabilities == null) {
                this.capabilities = ImmutableSet.copyOf(Configurations.collectCapabilities(getConfiguration(),
                    Sets.<Capability>newHashSet(),
                    Sets.<Configuration>newHashSet()));
            }
            return capabilities;
        }

        @Override
        public Set<ExcludeRule> getGlobalExcludes() {
            if (excludeRules == null) {
                this.excludeRules = ImmutableSet.copyOf(((ConfigurationInternal) getConfiguration()).getAllExcludeRules());
            }
            return excludeRules;
        }

        private Configuration getConfiguration() {
            return configurations.getByName(configurationName);
        }
    }

    private class BackwardsCompatibilityUsageContext extends AbstractUsageContext {
        private final DependencySet runtimeDependencies;

        private BackwardsCompatibilityUsageContext(String usageName, DependencySet runtimeDependencies) {
            super(usageName);
            this.runtimeDependencies = runtimeDependencies;
        }

        @Override
        public String getName() {
            return getUsage().getName();
        }

        @Override
        public Set<ModuleDependency> getDependencies() {
            return runtimeDependencies.withType(ModuleDependency.class);
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

    /**
     * This is only here to provide backward compatibility for the Shadow plugin. Remove in 5.0.
     */
    private static class BackwardsCompatibilityIsolatableFactory implements IsolatableFactory {
        @Override
        public <T> Isolatable<T> isolate(final T value) {
            return new Isolatable<T>() {
                @Override
                public T isolate() {
                    return value;
                }

                @Nullable
                @Override
                public <S> Isolatable<S> coerce(Class<S> type) {
                    return null;
                }

                @Override
                public void appendToHasher(Hasher hasher) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
