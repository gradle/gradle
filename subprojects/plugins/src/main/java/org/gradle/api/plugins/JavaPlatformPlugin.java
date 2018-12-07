/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.plugins;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.java.usagecontext.ConfigurationUsageContext;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

/**
 * The Java platform plugin allows building platform components
 * for Java, which are usually published as BOM files (for Maven)
 * or Gradle platforms (Gradle metadata).
 *
 * @since 5.2
 */
@Incubating
public class JavaPlatformPlugin implements Plugin<Project> {
    public static final String API_CONFIGURATION_NAME = "api";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";
    public static final String CLASSPATH_CONFIGURATION_NAME = "classpath";

    private static final Action<Configuration> AS_CONSUMABLE_CONFIGURATION = new Action<Configuration>() {
        @Override
        public void execute(Configuration conf) {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(true);
        }
    };

    private static final Action<Configuration> AS_RESOLVABLE_CONFIGURATION = new Action<Configuration>() {
        @Override
        public void execute(Configuration conf) {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
        }
    };

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        createConfigurations(project);
        createSoftwareComponent(project);
    }

    private boolean createSoftwareComponent(Project project) {
        return project.getComponents().add(project.getObjects().newInstance(JavaPlatformComponent.class, project.getConfigurations()));
    }

    private void createConfigurations(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration api = configurations.create(API_CONFIGURATION_NAME, AS_CONSUMABLE_CONFIGURATION);
        Configuration runtime = project.getConfigurations().create(RUNTIME_CONFIGURATION_NAME, AS_CONSUMABLE_CONFIGURATION);
        runtime.extendsFrom(api);
        Configuration classpath = configurations.create(CLASSPATH_CONFIGURATION_NAME, AS_RESOLVABLE_CONFIGURATION);
        classpath.extendsFrom(runtime);
        classpath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
    }

    public static class JavaPlatformComponent implements SoftwareComponentInternal {
        private final ObjectFactory objectFactory;
        private final ImmutableAttributesFactory attributesFactory;
        private final ConfigurationContainer configurations;
        private final UsageContext api;
        private final UsageContext runtime;

        @Inject
        public JavaPlatformComponent(ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory, ConfigurationContainer configurations) {
            this.objectFactory = objectFactory;
            this.attributesFactory = attributesFactory;
            this.configurations = configurations;
            this.api = createApiUsageContext();
            this.runtime = createRuntimeUsageContext();
        }

        @Override
        public Set<? extends UsageContext> getUsages() {
            return ImmutableSet.of(api, runtime);
        }

        @Override
        public String getName() {
            return "javaPlatform";
        }

        private UsageContext createRuntimeUsageContext() {
            return new JavaPlatformUsageContext(Usage.JAVA_RUNTIME, "runtime", RUNTIME_CONFIGURATION_NAME, configurations, objectFactory, attributesFactory);
        }

        private UsageContext createApiUsageContext() {
            return new JavaPlatformUsageContext(Usage.JAVA_API, "api", API_CONFIGURATION_NAME, configurations, objectFactory, attributesFactory);
        }
    }

    private final static class JavaPlatformUsageContext extends ConfigurationUsageContext {
        private final AttributeContainer attributes;

        private JavaPlatformUsageContext(String usageName,
                                        String name,
                                        String configurationName,
                                        ConfigurationContainer configurations,
                                        ObjectFactory objectFactory,
                                        ImmutableAttributesFactory attributesFactory) {
            super(usageName, name, configurationName, Collections.<PublishArtifact>emptySet(), configurations, objectFactory, attributesFactory);
            AttributeContainerInternal attributes = (AttributeContainerInternal) super.getAttributes();
            // Currently, "enforced" platforms are handled through special casing, so we don't need to publish the enforced version
            this.attributes = attributesFactory.concat(attributes.asImmutable(), PlatformSupport.COMPONENT_CATEGORY, PlatformSupport.REGULAR_PLATFORM);
        }

        @Override
        public AttributeContainer getAttributes() {
            return attributes;
        }
    }
}
