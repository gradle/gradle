/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRole;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;

import static org.gradle.api.attributes.DocsType.JAVADOC;
import static org.gradle.api.attributes.DocsType.SOURCES;
import static org.gradle.api.plugins.internal.JvmPluginsHelper.configureJavaDocTask;

public class DefaultJvmVariantBuilder implements JvmVariantBuilder {
    private final String name;
    private final SourceSet sourceSet;
    private final JvmPluginServices jvmPluginServices;
    private final RoleBasedConfigurationContainerInternal configurations;
    private final TaskContainer tasks;
    private final SoftwareComponentContainer components;
    private final ProjectInternal project;
    private String displayName;
    private boolean javadocJar;
    private boolean sourcesJar;
    private boolean published;
    private boolean overrideDefaultCapability = true;
    private final List<Capability> capabilities = Lists.newArrayListWithExpectedSize(2);

    @Inject
    public DefaultJvmVariantBuilder(String name,
                                    SourceSet sourceSet,
                                    Capability defaultCapability,
                                    JvmPluginServices jvmPluginServices,
                                    ConfigurationContainer configurations,
                                    TaskContainer tasks,
                                    SoftwareComponentContainer components,
                                    // Ideally we should use project but more specific components
                                    // like the previous parameters. However for project derived
                                    // capabilities we need a handle on the project coordinates
                                    // which can't be externalized. So we use Project but must
                                    // reduce the scope to this usage only so that we can replace
                                    // it later
                                    ProjectInternal project) {
        this.name = name;
        this.sourceSet = sourceSet;
        this.jvmPluginServices = jvmPluginServices;
        this.configurations = (RoleBasedConfigurationContainerInternal) configurations;
        this.tasks = tasks;
        this.components = components;
        this.project = project;
        this.capabilities.add(defaultCapability);
    }

    @Override
    public JvmVariantBuilder withDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public JvmVariantBuilder withJavadocJar() {
        javadocJar = true;
        return this;
    }

    @Override
    public JvmVariantBuilder withSourcesJar() {
        sourcesJar = true;
        return this;
    }

    @Override
    public JvmVariantBuilder capability(Capability capability) {
        if (capability.getVersion() == null) {
            throw new InvalidUserDataException("Capabilities declared on outgoing variants must have a version");
        }
        if (overrideDefaultCapability) {
            capabilities.clear();
            overrideDefaultCapability = false;
        }
        capabilities.add(capability);
        return this;
    }

    @Override
    public JvmVariantBuilder distinctCapability() {
        return capability(new ProjectDerivedCapability(project, name));
    }

    @Override
    public JvmVariantBuilder published() {
        published = true;
        return this;
    }

    void build() {
        boolean mainSourceSet = SourceSet.isMain(sourceSet);
        String apiConfigurationName;
        String implementationConfigurationName;
        String apiElementsConfigurationName;
        String runtimeElementsConfigurationName;
        String compileOnlyConfigurationName;
        String compileOnlyApiConfigurationName;
        String runtimeOnlyConfigurationName;
        if (mainSourceSet) {
            apiConfigurationName = name + "Api";
            implementationConfigurationName = name + "Implementation";
            apiElementsConfigurationName = apiConfigurationName + "Elements";
            runtimeElementsConfigurationName = name + "RuntimeElements";
            compileOnlyConfigurationName = name + "CompileOnly";
            compileOnlyApiConfigurationName = name + "CompileOnlyApi";
            runtimeOnlyConfigurationName = name + "RuntimeOnly";
        } else {
            apiConfigurationName = sourceSet.getApiConfigurationName();
            implementationConfigurationName = sourceSet.getImplementationConfigurationName();
            apiElementsConfigurationName = sourceSet.getApiElementsConfigurationName();
            runtimeElementsConfigurationName = sourceSet.getRuntimeElementsConfigurationName();
            compileOnlyConfigurationName = sourceSet.getCompileOnlyConfigurationName();
            compileOnlyApiConfigurationName = sourceSet.getCompileOnlyApiConfigurationName();
            runtimeOnlyConfigurationName = sourceSet.getRuntimeOnlyConfigurationName();
        }

        String displayName = this.displayName == null ? name : this.displayName;
        // In the general case, the following configurations are already created
        // but if we're using the "main" source set, it means that the component we're creating shares
        // the same source set (main) but declares its dependencies in its own buckets, so we need
        // to create them
        Configuration implementation = bucket("Implementation", implementationConfigurationName, displayName);
        Configuration compileOnly = bucket("Compile-Only", compileOnlyConfigurationName, displayName);
        Configuration compileOnlyApi = bucket("Compile-Only API", compileOnlyApiConfigurationName, displayName);
        Configuration runtimeOnly = bucket("Runtime-Only", runtimeOnlyConfigurationName, displayName);

        TaskProvider<Task> jarTask = registerOrGetJarTask(sourceSet, displayName);
        Configuration api = bucket("API", apiConfigurationName, displayName);
        implementation.extendsFrom(api);

        // A consumable which is deprecated for declaration.
        ConfigurationRole elementsRole = ConfigurationRole.forUsage(true, false, true, false, false, true);
        TaskProvider<JavaCompile> compileJava = tasks.named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);

        Configuration apiElements = configurations.maybeCreateWithRole(apiElementsConfigurationName, elementsRole, false, false);
        apiElements.setVisible(false);
        jvmPluginServices.useDefaultTargetPlatformInference(apiElements, compileJava);
        jvmPluginServices.configureAsApiElements(apiElements);
        apiElements.setDescription("API elements for " + displayName);
        apiElements.extendsFrom(api, compileOnlyApi);
        capabilities.forEach(apiElements.getOutgoing()::capability);
        jvmPluginServices.configureClassesDirectoryVariant(apiElements, sourceSet);
        apiElements.getOutgoing().artifact(jarTask);

        Configuration runtimeElements = configurations.maybeCreateWithRole(runtimeElementsConfigurationName, elementsRole, false, false);
        runtimeElements.setVisible(false);
        jvmPluginServices.useDefaultTargetPlatformInference(runtimeElements, compileJava);
        jvmPluginServices.configureAsRuntimeElements(runtimeElements);
        runtimeElements.setDescription("Runtime elements for " + displayName);
        runtimeElements.extendsFrom(implementation, runtimeOnly);
        capabilities.forEach(runtimeElements.getOutgoing()::capability);
        runtimeElements.getOutgoing().artifact(jarTask);

        if (mainSourceSet) {
            // we need to wire the compile only and runtime only to the classpath configurations
            configurations.getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(implementation, compileOnly);
            configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(implementation, runtimeOnly);
            // and we also want the feature dependencies to be available on the test classpath
            configurations.getByName(JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(implementation, compileOnlyApi);
            configurations.getByName(JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(implementation, runtimeOnly);
        }

        // TODO: #23495 Investigate the implications of using this class without
        //       the java plugin applied, and thus no java component present.
        //       In the long run, all of these variants and domain objects should be
        //       owned by a component. If we do not add them to the default java component,
        //       we should be adding them to a user-provided or new component instead.
        final AdhocComponentWithVariants component = findJavaComponent();

        JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
        configureJavaDocTask(name, sourceSet, tasks, javaPluginExtension);
        if (javadocJar) {
            Configuration javadocVariant = JvmPluginsHelper.createDocumentationVariantWithArtifact(
                sourceSet.getJavadocElementsConfigurationName(),
                mainSourceSet ? null : name,
                JAVADOC,
                capabilities,
                sourceSet.getJavadocJarTaskName(),
                tasks.named(sourceSet.getJavadocTaskName()),
                project
            );

            if (component != null) {
                component.addVariantsFromConfiguration(javadocVariant, new JavaConfigurationVariantMapping("runtime", true));
            }
        }
        if (sourcesJar) {
            Configuration sourcesVariant = JvmPluginsHelper.createDocumentationVariantWithArtifact(
                sourceSet.getSourcesElementsConfigurationName(),
                mainSourceSet ? null : name,
                SOURCES,
                capabilities,
                sourceSet.getSourcesJarTaskName(),
                sourceSet.getAllSource(),
                project
            );
            if (component != null) {
                component.addVariantsFromConfiguration(sourcesVariant, new JavaConfigurationVariantMapping("runtime", true));
            }
        }

        if (published && component != null) {
            component.addVariantsFromConfiguration(apiElements, new JavaConfigurationVariantMapping("compile", true));
            component.addVariantsFromConfiguration(runtimeElements, new JavaConfigurationVariantMapping("runtime", true));
        }
    }

    private Configuration bucket(String kind, String configName, String displayName) {
        Configuration configuration = configurations.maybeCreateWithRole(configName, ConfigurationRoles.INTENDED_BUCKET, false, false);
        configuration.setDescription(kind + " dependencies for " + displayName);
        configuration.setVisible(false);
        return configuration;
    }

    private TaskProvider<Task> registerOrGetJarTask(SourceSet sourceSet, String displayName) {
        String jarTaskName = sourceSet.getJarTaskName();
        if (!tasks.getNames().contains(jarTaskName)) {
            tasks.register(jarTaskName, Jar.class, jar -> {
                jar.setDescription("Assembles a jar archive containing the classes of the '" + displayName + "'.");
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(sourceSet.getOutput());
                jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(name));
            });
        }
        return tasks.named(jarTaskName);
    }

    @Nullable
    public AdhocComponentWithVariants findJavaComponent() {
        SoftwareComponent component = components.findByName("java");
        if (component instanceof AdhocComponentWithVariants) {
            return (AdhocComponentWithVariants) component;
        }
        return null;
    }

}
