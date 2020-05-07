/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.buildinit.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.userinput.UserInputHandler;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.buildinit.plugins.internal.BuildConverter;
import org.gradle.buildinit.plugins.internal.BuildInitializer;
import org.gradle.buildinit.plugins.internal.InitSettings;
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

import static org.gradle.buildinit.plugins.internal.PackageNameBuilder.toPackageName;

/**
 * Generates a Gradle project structure.
 */
public class InitBuild extends DefaultTask {
    private final String projectDirName = getProject().getProjectDir().getName();
    private final Property<String> type = getProject().getObjects().property(String.class);
    private final Property<String> dsl = getProject().getObjects().property(String.class);
    private final Property<String> testFramework = getProject().getObjects().property(String.class);
    private final Property<String> projectName = getProject().getObjects().property(String.class);
    private final Property<String> packageName = getProject().getObjects().property(String.class);

    private final ProviderFactory providerFactory;
    private UserInputHandler inputHandler;
    private ProjectLayoutSetupRegistry projectLayoutRegistry;

    public InitBuild() {
        this.providerFactory = getProject().getProviders();
        this.projectLayoutRegistry = getServices().get(ProjectLayoutSetupRegistry.class);
    }

    @Internal
    public ProjectLayoutSetupRegistry getProjectLayoutRegistry() {
        return projectLayoutRegistry;
    }

    /**
     * The desired type of project to generate, defaults to 'pom' if a 'pom.xml' is found in the project root and if no 'pom.xml' is found, it defaults to 'basic'.
     *
     * This property can be set via command-line option '--type'.
     */
    @Input
    public String getType() {
        return type.orElse(providerFactory.provider(this::detectType)).get();
    }

    /**
     * The desired DSL of build scripts to create, defaults to 'groovy'.
     *
     * This property can be set via command-line option '--dsl'.
     *
     * @since 4.5
     */
    @Optional
    @Input
    public String getDsl() {
        return dsl.getOrElse(BuildInitDsl.GROOVY.getId());
    }

    /**
     * The name of the generated project, defaults to the name of the directory the project is generated in.
     *
     * This property can be set via command-line option '--project-name'.
     *
     * @since 5.0
     */
    @Input
    public String getProjectName() {
        return projectName.getOrElse(projectDirName);
    }

    /**
     * The name of the package to use for generated source.
     *
     * This property can be set via command-line option '--package'.
     *
     * @since 5.0
     */
    @Input
    public String getPackageName() {
        return packageName.getOrElse("");
    }

    /**
     * The test framework to be used in the generated project.
     *
     * This property can be set via command-line option '--test-framework'
     */
    @Nullable
    @Optional
    @Input
    public String getTestFramework() {
        return testFramework.getOrNull();
    }

    @TaskAction
    public void setupProjectLayout() {
        this.inputHandler = getServices().get(UserInputHandler.class);

        BuildInitializer initDescriptor = provideBuildType().get();
        BuildInitDsl dsl = provideDsl(initDescriptor).get();
        BuildInitTestFramework testFramework = provideTestFramework(initDescriptor).getOrNull();
        String projectName = provideProjectName(initDescriptor).get();
        String packageName = providePackageName(initDescriptor, projectName).get();

        initDescriptor.generate(new InitSettings(projectName, dsl, packageName, testFramework));

        initDescriptor.getFurtherReading().ifPresent(link -> getLogger().lifecycle("Get more help with your project: {}", link));
    }

    @Option(option = "type", description = "Set the type of project to generate.")
    public void setType(String type) {
        this.type.set(type);
    }

    @OptionValues("type")
    @SuppressWarnings("unused")
    public List<String> getAvailableBuildTypes() {
        return projectLayoutRegistry.getAllTypes();
    }

    /**
     * Set the build script DSL to be used.
     *
     * @since 4.5
     */
    @Option(option = "dsl", description = "Set the build script DSL to be used in generated scripts.")
    public void setDsl(String dsl) {
        this.dsl.set(dsl);
    }

    /**
     * Available build script DSLs to be used.
     *
     * @since 4.5
     */
    @OptionValues("dsl")
    @SuppressWarnings("unused")
    public List<String> getAvailableDSLs() {
        return BuildInitDsl.listSupported();
    }

    /**
     * Set the test framework to be used.
     */
    @Option(option = "test-framework", description = "Set the test framework to be used.")
    public void setTestFramework(@Nullable String testFramework) {
        this.testFramework.set(testFramework);
    }

    /**
     * Available test frameworks.
     */
    @OptionValues("test-framework")
    @SuppressWarnings("unused")
    public List<String> getAvailableTestFrameworks() {
        return BuildInitTestFramework.listSupported();
    }

    /**
     * Set the project name.
     *
     * @since 5.0
     */
    @Option(option = "project-name", description = "Set the project name.")
    public void setProjectName(String projectName) {
        this.projectName.set(projectName);
    }

    /**
     * Set the package name.
     *
     * @since 5.0
     */
    @Option(option = "package", description = "Set the package for source files.")
    public void setPackageName(String packageName) {
        this.packageName.set(packageName);
    }

    void setProjectLayoutRegistry(ProjectLayoutSetupRegistry projectLayoutRegistry) {
        this.projectLayoutRegistry = projectLayoutRegistry;
    }

    private Provider<BuildInitializer> provideBuildType() {
        return type.map(t -> projectLayoutRegistry.get(t)).orElse(providerFactory.provider(() -> {
            BuildConverter converter = projectLayoutRegistry.getBuildConverter();
            if (converter.canApplyToCurrentDirectory()) {
                if (inputHandler.askYesNoQuestion("Found a " + converter.getSourceBuildDescription() + " build. Generate a Gradle build from this?", true)) {
                    return converter;
                }
            }

            ComponentType componentType = selectInteractive("Select type of project to generate",
                projectLayoutRegistry.getComponentTypes(), projectLayoutRegistry.getDefault().getComponentType());
            List<Language> languages = projectLayoutRegistry.getLanguagesFor(componentType);
            if (languages.size() != 1 && !languages.contains(Language.JAVA)) {
                // Not yet implemented
                throw new UnsupportedOperationException();
            }
            Language language = selectInteractive("Select implementation language", languages, Language.JAVA);
            return projectLayoutRegistry.get(componentType, language);
        }));
    }

    private Provider<BuildInitDsl> provideDsl(BuildInitializer initDescriptor) {
        return dsl.map(dslName -> {
            BuildInitDsl buildInitDsl = BuildInitDsl.fromName(dslName);
            if (!initDescriptor.getDsls().contains(buildInitDsl)) {
                throw new GradleException("The requested DSL '" + buildInitDsl.getId() + "' is not supported for '" + initDescriptor.getId() + "' build type");
            }
            return buildInitDsl;
        }).orElse(provideInteractive("Select build script DSL", initDescriptor.getDsls(), initDescriptor.getDefaultDsl()))
            .orElse(providerFactory.provider(() -> BuildInitDsl.fromName(getDsl())));
    }

    private Provider<BuildInitTestFramework> provideTestFramework(BuildInitializer initDescriptor) {
        return testFramework.map(frameworkName -> {
            BuildInitTestFramework buildInitTestFramework = BuildInitTestFramework.fromName(frameworkName);
            if (buildInitTestFramework != BuildInitTestFramework.NONE && initDescriptor.getTestFrameworks().contains(buildInitTestFramework)) {
                return buildInitTestFramework;
            }

            TreeFormatter formatter = new TreeFormatter();
            formatter.node("The requested test framework '" + getTestFramework() + "' is not supported for '" + initDescriptor.getId() + "' build type. Supported frameworks");
            formatter.startChildren();
            for (BuildInitTestFramework framework : initDescriptor.getTestFrameworks()) {
                formatter.node("'" + framework.getId() + "'");
            }
            formatter.endChildren();
            throw new GradleException(formatter.toString());
        }).orElse(provideInteractive("Select test framework", initDescriptor.getTestFrameworks(), initDescriptor.getDefaultTestFramework()));
    }

    private Provider<String> provideProjectName(BuildInitializer initDescriptor) {
        if (!initDescriptor.supportsProjectName() && projectName.isPresent()) {
            throw new GradleException("Project name is not supported for '" + initDescriptor.getId() + "' build type.");
        }
        return projectName.orElse(provideInteractive("Project name", getProjectName()));
    }

    private Provider<String> providePackageName(BuildInitializer initDescriptor, String projectName) {
        if (!initDescriptor.supportsPackage() && packageName.isPresent()) {
            throw new GradleException("Package name is not supported for '" + initDescriptor.getId() + "' build type.");
        }
        return packageName.orElse(provideInteractive("Source package", toPackageName(projectName)));
    }

    private Provider<String> provideInteractive(String prompt, String defaultValue) {
        return providerFactory.provider(() -> inputHandler.askQuestion(prompt, defaultValue));
    }

    private <T> Provider<T> provideInteractive(String prompt, Collection<T> options, T defaultValue) {
        return providerFactory.provider(() -> selectInteractive(prompt, options, defaultValue));
    }

    private <T> T selectInteractive(String prompt, Collection<T> options, T defaultValue) {
        if (options.size() == 1) {
            return options.iterator().next();
        }
        if (options.size() > 1) {
            return inputHandler.selectOption(prompt, options, defaultValue);
        }
        return defaultValue;
    }

    private String detectType() {
        BuildConverter buildConverter = projectLayoutRegistry.getBuildConverter();
        if (buildConverter.canApplyToCurrentDirectory()) {
            return buildConverter.getId();
        }
        return projectLayoutRegistry.getDefault().getId();
    }
}
