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
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.util.TextUtil;

import java.util.List;

import static org.gradle.api.attributes.Bundling.EXTERNAL;
import static org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.plugins.internal.JavaPluginsHelper.registerClassesDirVariant;

public class DefaultJavaFeatureSpec implements FeatureSpecInternal {
    private final String name;
    private final JavaPluginConvention javaPluginConvention;
    private final ConfigurationContainer configurationContainer;
    private final ObjectFactory objectFactory;
    private final PluginManager pluginManager;
    private final SoftwareComponentContainer components;
    private final TaskContainer tasks;
    private final List<Capability> capabilities = Lists.newArrayListWithExpectedSize(2);

    private boolean overrideDefaultCapability = true;
    private SourceSet sourceSet;

    public DefaultJavaFeatureSpec(String name,
                                  Capability defaultCapability,
                                  JavaPluginConvention javaPluginConvention,
                                  ConfigurationContainer configurationContainer,
                                  ObjectFactory objectFactory,
                                  PluginManager pluginManager,
                                  SoftwareComponentContainer components,
                                  TaskContainer tasks) {
        this.name = name;
        this.javaPluginConvention = javaPluginConvention;
        this.configurationContainer = configurationContainer;
        this.objectFactory = objectFactory;
        this.pluginManager = pluginManager;
        this.components = components;
        this.tasks = tasks;
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

    private void setupConfigurations(SourceSet sourceSet) {
        if (sourceSet == null) {
            throw new InvalidUserCodeException("You must specify which source set to use for feature '" + name + "'");
        }
        String apiConfigurationName;
        String implConfigurationName;
        String apiElementsConfigurationName;
        String runtimeElementsConfigurationName;
        boolean mainSourceSet = isMainSourceSet(sourceSet);
        if (mainSourceSet) {
            apiConfigurationName = name + "Api";
            implConfigurationName = name + "Implementation";
            apiElementsConfigurationName = apiConfigurationName + "Elements";
            runtimeElementsConfigurationName = name + "RuntimeElements";
        } else {
            apiConfigurationName = sourceSet.getApiConfigurationName();
            implConfigurationName = sourceSet.getImplementationConfigurationName();
            apiElementsConfigurationName = sourceSet.getApiElementsConfigurationName();
            runtimeElementsConfigurationName = sourceSet.getRuntimeElementsConfigurationName();
        }
        final Configuration api = bucket("API", apiConfigurationName);
        Configuration impl = bucket("Implementation", implConfigurationName);
        impl.extendsFrom(api);
        final Configuration apiElements = export(apiElementsConfigurationName);
        apiElements.extendsFrom(api);
        final Configuration runtimeElements = export(runtimeElementsConfigurationName);
        runtimeElements.extendsFrom(impl);
        configureUsage(apiElements, Usage.JAVA_API_JARS);
        configureUsage(runtimeElements, Usage.JAVA_RUNTIME_JARS);
        configurePacking(apiElements);
        configurePacking(runtimeElements);
        configureTargetPlatform(apiElements);
        configureTargetPlatform(runtimeElements);
        configureCategory(apiElements);
        configureCategory(runtimeElements);
        configureCapabilities(apiElements);
        configureCapabilities(runtimeElements);
        attachArtifactToConfiguration(apiElements);
        attachArtifactToConfiguration(runtimeElements);

        String javaCompileTaskName = sourceSet.getCompileJavaTaskName();
        Provider<JavaCompile> javaCompile = tasks.named(javaCompileTaskName, JavaCompile.class);
        registerClassesDirVariant(javaCompile, objectFactory, apiElements);

        if (mainSourceSet) {
            // since we use the main source set, we need to make sure the compile classpath and runtime classpath are properly configured
            configurationContainer.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(impl);
            configurationContainer.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(impl);
        }

        pluginManager.withPlugin("maven-publish", new Action<AppliedPlugin>() {
            @Override
            public void execute(AppliedPlugin plugin) {
                final AdhocComponentWithVariants component = findComponent();
                if (component != null) {
                    component.addVariantsFromConfiguration(apiElements, new JavaConfigurationVariantMapping("compile", true));
                    component.addVariantsFromConfiguration(runtimeElements, new JavaConfigurationVariantMapping("runtime", true));
                }
            }
        });
    }

    private void configureTargetPlatform(Configuration configuration) {
        ((ConfigurationInternal)configuration).beforeLocking(new Action<ConfigurationInternal>() {
            @Override
            public void execute(ConfigurationInternal configuration) {
                String majorVersion = javaPluginConvention.getTargetCompatibility().getMajorVersion();
                AttributeContainerInternal attributes = configuration.getAttributes();
                // If nobody said anything about this variant's target platform, use whatever the convention says
                if (!attributes.contains(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)) {
                    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.valueOf(majorVersion));
                }
            }
        });
    }

    private void configurePacking(Configuration configuration) {
        configuration.getAttributes().attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, EXTERNAL));
    }

    private void configureCategory(Configuration configuration) {
        configuration.getAttributes().attribute(CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, LIBRARY));
    }

    private void attachArtifactToConfiguration(Configuration configuration) {
        String jarTaskName = sourceSet.getJarTaskName();
        if (!tasks.getNames().contains(jarTaskName)) {
            tasks.register(jarTaskName, Jar.class, new Action<Jar>() {
                @Override
                public void execute(Jar jar) {
                    jar.setDescription("Assembles a jar archive containing the classes of the '" + name + "' feature.");
                    jar.setGroup(BasePlugin.BUILD_GROUP);
                    jar.from(sourceSet.getOutput());
                    jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(name));
                }
            });
        }
        TaskProvider<Task> jar = tasks.named(jarTaskName);
        configuration.getArtifacts().add(new LazyPublishArtifact(jar));
    }

    private AdhocComponentWithVariants findComponent() {
        SoftwareComponent component = components.findByName("java");
        if (component instanceof AdhocComponentWithVariants) {
            return (AdhocComponentWithVariants) component;
        }
        return null;
    }

    private void configureCapabilities(Configuration apiElements) {
        for (Capability capability : capabilities) {
            apiElements.getOutgoing().capability(capability);
        }
    }

    private Configuration export(String configName) {
        Configuration configuration = configurationContainer.maybeCreate(configName);
        configuration.setCanBeConsumed(true);
        configuration.setCanBeResolved(false);
        return configuration;
    }

    private Configuration bucket(String kind, String configName) {
        Configuration configuration = configurationContainer.maybeCreate(configName);
        configuration.setDescription(kind + " dependencies for feature " + name);
        configuration.setCanBeResolved(false);
        configuration.setCanBeConsumed(false);
        return configuration;
    }

    private void configureUsage(Configuration conf, final String usage) {
        conf.attributes(new Action<AttributeContainer>() {
            @Override
            public void execute(AttributeContainer attrs) {
                attrs.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, usage));
            }
        });
    }

    private boolean isMainSourceSet(SourceSet sourceSet) {
        SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        return mainSourceSet.equals(sourceSet);
    }

}
