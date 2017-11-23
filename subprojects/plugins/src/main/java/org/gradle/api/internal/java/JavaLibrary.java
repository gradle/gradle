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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.api.internal.changedetection.state.isolation.IsolatableFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.model.DefaultObjectFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.model.ObjectFactory;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME;

/**
 * A SoftwareComponent representing a library that runs on a java virtual machine.
 */
public class JavaLibrary implements SoftwareComponentInternal {

    // This must ONLY be used in the deprecated constructor, for backwards compatibility
    private final static ObjectFactory DEPRECATED_OBJECT_FACTORY = new DefaultObjectFactory(DirectInstantiator.INSTANCE, NamedObjectInstantiator.INSTANCE);

    private final Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final UsageContext runtimeUsage;
    private final UsageContext compileUsage;
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;

    @Inject
    public JavaLibrary(ObjectFactory objectFactory, ConfigurationContainer configurations, ImmutableAttributesFactory attributesFactory, PublishArtifact artifact) {
        this.artifacts.add(artifact);
        this.configurations = configurations;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.runtimeUsage = new RuntimeUsageContext(Usage.JAVA_RUNTIME);
        this.compileUsage = new CompileUsageContext(Usage.JAVA_API);
    }

    /**
     * This constructor should not be used, and is maintained only for backwards
     * compatibility with the widely used Shadow plugin.
     */
    @Deprecated
    public JavaLibrary(PublishArtifact jarArtifact, DependencySet runtimeDependencies) {
        DeprecationLogger.nagUserOfDeprecatedThing("A constructor for `org.gradle.api.internal.java.JavaLibrary` is used by Shadow plugin v1.2.x, and has been preserved for compatibility",
            "If you're using the Shadow plugin, try upgrading to v2.x");
        this.artifacts.add(jarArtifact);
        this.objectFactory = DEPRECATED_OBJECT_FACTORY;
        this.attributesFactory = new DefaultImmutableAttributesFactory(new BackwardsCompatibilityIsolatableFactory());
        this.runtimeUsage = new BackwardsCompatibilityUsageContext(Usage.JAVA_RUNTIME, runtimeDependencies);
        this.compileUsage = new BackwardsCompatibilityUsageContext(Usage.JAVA_API, runtimeDependencies);
        this.configurations = null;
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

    private class RuntimeUsageContext extends AbstractUsageContext {
        private DependencySet dependencies;

        RuntimeUsageContext(String usageName) {
            super(usageName);
        }

        @Override
        public String getName() {
            return "runtime";
        }

        public Set<ModuleDependency> getDependencies() {
            if (dependencies == null) {
                dependencies = configurations.getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME).getIncoming().getDependencies();
            }
            return dependencies.withType(ModuleDependency.class);
        }
    }

    private class CompileUsageContext extends AbstractUsageContext {
        private DependencySet dependencies;

        CompileUsageContext(String usageName) {
            super(usageName);
        }

        @Override
        public String getName() {
            return "api";
        }

        public Set<ModuleDependency> getDependencies() {
            if (dependencies == null) {
                dependencies = configurations.getByName(API_ELEMENTS_CONFIGURATION_NAME).getIncoming().getDependencies();
            }
            return dependencies.withType(ModuleDependency.class);
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
                public void appendToHasher(BuildCacheHasher hasher) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
