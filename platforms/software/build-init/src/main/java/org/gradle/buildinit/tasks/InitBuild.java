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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.tasks.userinput.NonInteractiveUserInputHandler;
import org.gradle.api.internal.tasks.userinput.UserInputHandler;
import org.gradle.api.internal.tasks.userinput.UserQuestions;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.api.tasks.wrapper.internal.WrapperDefaults;
import org.gradle.api.tasks.wrapper.internal.WrapperGenerator;
import org.gradle.buildinit.InsecureProtocolOption;
import org.gradle.buildinit.plugins.internal.BuildConverter;
import org.gradle.buildinit.plugins.internal.BuildGenerator;
import org.gradle.buildinit.plugins.internal.BuildInitException;
import org.gradle.buildinit.plugins.internal.BuildInitializer;
import org.gradle.buildinit.plugins.internal.GenerationSettings;
import org.gradle.buildinit.plugins.internal.InitSettings;
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;
import org.gradle.buildinit.specs.BuildInitConfig;
import org.gradle.buildinit.specs.BuildInitGenerator;
import org.gradle.buildinit.specs.BuildInitParameter;
import org.gradle.buildinit.specs.BuildInitSpec;
import org.gradle.buildinit.specs.internal.BuildInitSpecRegistry;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.util.GradleVersion;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Generates a Gradle project structure.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class InitBuild extends DefaultTask {
    private static final String SOURCE_PACKAGE_DEFAULT = "org.example";
    private static final String SOURCE_PACKAGE_PROPERTY = "org.gradle.buildinit.source.package";
    private static final int MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API = 7;
    private static final int DEFAULT_JAVA_VERSION = 21;

    // To be exposed as property, see https://github.com/gradle/gradle/issues/22625
    private final Directory projectDir = getProject().getLayout().getProjectDirectory();
    @Internal
    private ProjectLayoutSetupRegistry projectLayoutRegistry;

    /**
     * Should default values automatically be accepted for options that are not configured explicitly?
     * <p>
     * When true, the interactive dialog is skipped, and no user input is required to complete the command.
     * <p>
     * This property can be set via the command-line options '--use-defaults' and '--no-use-defaults'.
     *
     * @since 8.6
     */
    @Incubating
    @Input
    @Optional
    @Option(option = "use-defaults", description = "Use default values for options not configured explicitly")
    public abstract Property<Boolean> getUseDefaults();

    /**
    * Should we allow existing files in the build directory to be overwritten?
    *
    * This property can be set via command-line option '--overwrite'. Defaults to false.
    *
    * @since 8.9
    */
    @Incubating
    @Input
    @Optional
    @Option(option = "overwrite", description = "Allow existing files in the build directory to be overwritten?")
    public abstract Property<Boolean> getAllowFileOverwrite();

    /**
     * The desired type of project to generate, such as 'java-application' or 'kotlin-library'.
     * <p>
     * Defaults to 'basic' - a minimal scaffolding, following Gradle best practices.
     * If a `pom.xml` is found in the project root directory, the type defaults to 'pom'
     * and the existing project is converted to Gradle.
     * <p>
     * Possible values for the option are provided by {@link #getAvailableBuildTypes()}.
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    @Option(option = "type", description = "Set the type of project to generate.")
    public abstract Property<String> getType();

    /**
     * Should the build be split into multiple subprojects?
     *
     * This property can be set via the command-line options '--split-project'
     * and '--no-split-project'.
     *
     * @since 6.7
     */
    @Input
    @Optional
    @Option(option = "split-project", description = "Split functionality across multiple subprojects?")
    public abstract Property<Boolean> getSplitProject();

    /**
     * The desired DSL of build scripts to create, defaults to 'kotlin'.
     *
     * @since 4.5
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    @Option(option = "dsl", description = "Set the build script DSL to be used in generated scripts.")
    public abstract Property<String> getDsl();

    /**
     * Can the generated build use new and unstable features?
     *
     * When enabled, the generated build will use new patterns, APIs or features that
     * may be unstable between minor releases. Use this if you'd like to try out the
     * latest features of Gradle.
     *
     * By default, init will generate a build that uses stable features and behavior.
     *
     * @since 7.3
     */
    @Input
    @Optional
    @Option(option = "incubating", description = "Allow the generated build to use new features and APIs.")
    public abstract Property<Boolean> getUseIncubating();

    /**
     * Java version to be used by generated Java projects.
     *
     * When set, Gradle will use the provided value as the target major Java version
     * for all relevant generated projects.  Gradle will validate the number to ensure
     * it is a valid and supported major version.
     *
     * @return the java version number supplied by the user
     * @since 8.5
     */
    @Input
    @Optional
    @Incubating
    @Option(option = "java-version", description = "Provides java version to use in the project.")
    public abstract Property<String> getJavaVersion();

    /**
     * The name of the generated project, defaults to the name of the directory the project is generated in.
     *
     * @since 5.0
     */
    @Input
    @Optional
    @Option(option = "project-name", description = "Set the project name.")
    @ReplacesEagerProperty
    public abstract Property<String> getProjectName();

    /**
     * The name of the package to use for generated source.
     *
     * @since 5.0
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    @Option(option = "package", description = "Set the package for source files.")
    public abstract Property<String> getPackageName();

    /**
     * The test framework to be used in the generated project.
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    @Option(option = "test-framework", description = "Set the test framework to be used.")
    public abstract Property<String> getTestFramework();

    /**
     * How to handle insecure (http) URLs used for Maven Repositories.
     *
     * This property can be set via command-line option '--insecure-protocol'.  The default value is 'warn'.
     *
     * @since 7.3
     */
    @Input
    @Option(option = "insecure-protocol", description = "How to handle insecure URLs used for Maven Repositories.")
    public abstract Property<InsecureProtocolOption> getInsecureProtocol();

    /**
     * Should clarifying comments be added to files?
     * <p>
     * This property can be set via the command-line options '--comments' and '--no-comments'.
     *
     * @since 8.7
     */
    @Incubating
    @Input
    @Optional
    @Option(option = "comments", description = "Include clarifying comments in files.")
    public abstract Property<Boolean> getComments();

    @NotToBeReplacedByLazyProperty(because = "Injected service")
    public ProjectLayoutSetupRegistry getProjectLayoutRegistry() {
        if (projectLayoutRegistry == null) {
            projectLayoutRegistry = getServices().get(ProjectLayoutSetupRegistry.class);
        }

        return projectLayoutRegistry;
    }

    @TaskAction
    public void setupProjectLayout() {
        UserInputHandler inputHandler = getEffectiveInputHandler();
        if (shouldUseInitProjectSpec(inputHandler)) {
            doInitSpecProjectGeneration(inputHandler);
        } else {
            doStandardProjectGeneration(inputHandler);
        }
    }

    private boolean shouldUseInitProjectSpec(UserInputHandler inputHandler) {
        boolean templatesAvailable = !getBuildInitSpecRegistry().isEmpty();
        return templatesAvailable && inputHandler.askUser(uq -> uq.askBooleanQuestion("Additional project types were loaded.  Do you want to generate a project using a contributed project specification?", true)).get();
    }

    private void doInitSpecProjectGeneration(UserInputHandler inputHandler) {
        BuildInitConfig config = inputHandler.askUser(this::selectAndConfigureSpec).get();
        BuildInitGenerator generator = createGenerator(config);
        generator.generate(config, projectDir);
        generateWrapper();
    }

    private BuildInitConfig selectAndConfigureSpec(UserQuestions userQuestions) {
        BuildInitSpecRegistry registry = getBuildInitSpecRegistry();

        BuildInitSpec spec;
        if (!getType().isPresent()) {
            spec = userQuestions.choice("Select project type", registry.getAllSpecs())
                .renderUsing(BuildInitSpec::getDisplayName)
                .ask();
        }  else {
            spec = registry.getSpecByType(getType().get());
        }

        // TODO: Ask questions for each parameter, and return a configuration object with populated arguments
        return new BuildInitConfig() {
            @Override
            @Nonnull
            public BuildInitSpec getBuildSpec() {
                return spec;
            }

            @Override
            @Nonnull
            public Map<BuildInitParameter<?>, Object> getArguments() {
                return Collections.emptyMap();
            }
        };
    }

    private BuildInitGenerator createGenerator(BuildInitConfig config) {
        Class<? extends BuildInitGenerator> generator = getBuildInitSpecRegistry().getGeneratorForSpec(config.getBuildSpec());
        return getObjectFactory().newInstance(generator);
    }

    private void doStandardProjectGeneration(UserInputHandler inputHandler) {
        GenerationSettings settings = inputHandler.askUser(this::calculateGenerationSettings).get();

        boolean userInterrupted = inputHandler.interrupted();
        if (userInterrupted) {
            throw new BuildCancelledException();
        }

        settings.getInitializer().generate(settings.getSettings());
        generateWrapper();

        settings.getInitializer().getFurtherReading(settings.getSettings())
            .ifPresent(link -> getLogger().lifecycle(link));
    }

    private GenerationSettings calculateGenerationSettings(UserQuestions userQuestions) {
        validateBuildDirectory(userQuestions);

        ProjectLayoutSetupRegistry projectLayoutRegistry = getProjectLayoutRegistry();

        BuildInitializer initializer = getBuildInitializer(userQuestions, projectLayoutRegistry);

        JavaLanguageVersion javaLanguageVersion = getJavaLanguageVersion(userQuestions, initializer);

        String projectName = getEffectiveProjectName(userQuestions, initializer);

        ModularizationOption modularizationOption = getModularizationOption(userQuestions, initializer);

        BuildInitDsl dsl = getBuildInitDsl(userQuestions, initializer);

        BuildInitTestFramework testFramework = getBuildInitTestFramework(userQuestions, initializer, modularizationOption);

        String packageName = getEffectivePackageName(initializer);

        validatePackageName(packageName);

        boolean useIncubatingAPIs = shouldUseIncubatingAPIs(userQuestions);
        boolean generateComments = getComments().get();

        List<String> subprojectNames = initializer.getDefaultProjectNames();
        InitSettings initSettings = new InitSettings(
            projectName,
            useIncubatingAPIs,
            subprojectNames,
            modularizationOption,
            dsl,
            packageName,
            testFramework,
            getInsecureProtocol().get(),
            projectDir,
            javaLanguageVersion,
            generateComments
        );
        return new GenerationSettings(initializer, initSettings);
    }

    private void generateWrapper() {
        Directory projectDirectory = getLayout().getProjectDirectory();
        File unixScript = projectDirectory.file(WrapperDefaults.SCRIPT_PATH).getAsFile();
        File jarFile = projectDirectory.file(WrapperDefaults.JAR_FILE_PATH).getAsFile();
        String jarFileRelativePath = getRelativePath(projectDirectory.getAsFile(), jarFile);
        File propertiesFile = WrapperGenerator.getPropertiesFile(jarFile);
        String distributionUrl = WrapperGenerator.getDistributionUrl(GradleVersion.current(), WrapperDefaults.DISTRIBUTION_TYPE);
        WrapperGenerator.generate(
            WrapperDefaults.ARCHIVE_BASE, WrapperDefaults.ARCHIVE_PATH,
            WrapperDefaults.DISTRIBUTION_BASE, WrapperDefaults.DISTRIBUTION_PATH,
            null,
            propertiesFile,
            jarFile, jarFileRelativePath,
            unixScript, WrapperGenerator.getBatchScript(unixScript),
            distributionUrl,
            true,
            WrapperDefaults.NETWORK_TIMEOUT
        );
    }

    private static String getRelativePath(File baseDir, File targetFile) {
        return baseDir.toPath().relativize(targetFile.toPath()).toString();
    }

    private UserInputHandler getEffectiveInputHandler() {
        if (getUseDefaults().get()) {
            return new NonInteractiveUserInputHandler();
        }

        return getUserInputHandler();
    }

    /**
     * If not converting an existing Maven build, then validate the build directory is either
     * empty, or overwritable before generating the project.
     *
     * @param userQuestions the user questions to ask if {@link #getAllowFileOverwrite()} is not set and the directory is non-empty
     * @throws BuildInitException if the build directory is non-empty, this isn't a POM conversion and the user does not allow overwriting
     */
    private void validateBuildDirectory(UserQuestions userQuestions) {
        if (!isPomConversion()) {
            File projectDirFile = projectDir.getAsFile();
            File[] existingProjectFiles = projectDirFile.listFiles();

            boolean isNotEmptyDirectory = existingProjectFiles != null && existingProjectFiles.length != 0;
            if (isNotEmptyDirectory) {
                boolean fileOverwriteAllowed = getAllowFileOverwrite().get();
                if (!fileOverwriteAllowed) {
                    fileOverwriteAllowed = userQuestions.askBooleanQuestion("Found existing files in the project directory: '" + projectDirFile +
                        "'." + System.lineSeparator() + "Directory will be modified and existing files may be overwritten.  Continue?", false);
                }

                if (!fileOverwriteAllowed) {
                    abortBuildDueToExistingFiles(projectDirFile);
                }
            }
        }
    }

    private boolean isPomConversion() {
        return Objects.equals(detectType(), "pom");
    }

    private void abortBuildDueToExistingFiles(File projectDirFile) {
        List<String> resolutions = Arrays.asList("Remove any existing files in the project directory and run the init task again.", "Enable the --overwrite option to allow existing files to be overwritten.");
        throw new BuildInitException("Aborting build initialization due to existing files in the project directory: '" + projectDirFile + "'.", resolutions);
    }

    private static void validatePackageName(String packageName) {
        if (!isNullOrEmpty(packageName) && !SourceVersion.isName(packageName)) {
            throw new GradleException("Package name: '" + packageName + "' is not valid - it may contain invalid characters or reserved words.");
        }
    }

    @VisibleForTesting
    @Nullable
    JavaLanguageVersion getJavaLanguageVersion(UserQuestions userQuestions, BuildInitializer initializer) {
        if (!initializer.supportsJavaTargets()) {
            return null;
        }

        String version = getJavaVersion().getOrNull();
        if (isNullOrEmpty(version)) {
            return JavaLanguageVersion.of(userQuestions.askIntQuestion("Enter target Java version", MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API, DEFAULT_JAVA_VERSION));
        }

        try {
            int parsedVersion = Integer.parseInt(version);
            if (parsedVersion < MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API) {
                throw new GradleException("Target Java version: '" + version + "' is not a supported target version. It must be equal to or greater than " + MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API);
            }
            return JavaLanguageVersion.of(parsedVersion);
        } catch (NumberFormatException e) {
            throw new GradleException("Invalid target Java version '" + version + "'. The version must be an integer.", e);
        }
    }

    private BuildInitDsl getBuildInitDsl(UserQuestions userQuestions, BuildInitializer initializer) {
        BuildInitDsl dsl;
        if (!getDsl().isPresent()) {
            dsl = userQuestions.selectOption("Select build script DSL", initializer.getDsls(), initializer.getDefaultDsl());
        } else {
            dsl = BuildInitDsl.fromName(getDsl().get());
            if (!initializer.getDsls().contains(dsl)) {
                throw new GradleException("The requested DSL '" + getDsl().get() + "' is not supported for '" + initializer.getId() + "' build type");
            }
        }
        return dsl;
    }

    private ModularizationOption getModularizationOption(UserQuestions userQuestions, BuildInitializer initializer) {
        if (getSplitProject().isPresent()) {
            return getSplitProject().get() ? ModularizationOption.WITH_LIBRARY_PROJECTS : ModularizationOption.SINGLE_PROJECT;
        }
        return userQuestions.choice("Select application structure", initializer.getModularizationOptions())
            .renderUsing(ModularizationOption::getDisplayName)
            .ask();
    }

    private boolean shouldUseIncubatingAPIs(UserQuestions userQuestions) {
        if (getUseIncubating().isPresent()) {
            return getUseIncubating().get();
        }
        return userQuestions.askBooleanQuestion("Generate build using new APIs and behavior (some features may change in the next minor release)?", false);
    }

    private BuildInitTestFramework getBuildInitTestFramework(UserQuestions userQuestions, BuildInitializer initializer, ModularizationOption modularizationOption) {
        if (getTestFramework().isPresent()) {
            String testFramework = getTestFramework().get();
            return initializer.getTestFrameworks(modularizationOption).stream()
                .filter(candidate -> testFramework.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> createNotSupportedTestFrameWorkException(initializer, modularizationOption));
        }

        BuildInitTestFramework testFramework = initializer.getDefaultTestFramework(modularizationOption);
        return userQuestions.selectOption("Select test framework", initializer.getTestFrameworks(modularizationOption), testFramework);
    }

    private GradleException createNotSupportedTestFrameWorkException(BuildInitializer initDescriptor, ModularizationOption modularizationOption) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("The requested test framework '" + getTestFramework().get() + "' is not supported for '" + initDescriptor.getId() + "' build type. Supported frameworks");
        formatter.startChildren();
        for (BuildInitTestFramework framework : initDescriptor.getTestFrameworks(modularizationOption)) {
            formatter.node("'" + framework.getId() + "'");
        }
        formatter.endChildren();
        return new GradleException(formatter.toString());
    }

    @VisibleForTesting
    String getEffectiveProjectName(UserQuestions userQuestions, BuildInitializer initializer) {
        if (initializer.supportsProjectName()) {
            if (getProjectName().isPresent()) {
                return getProjectName().get();
            } else {
                return userQuestions.askQuestion("Project name", projectDir.getAsFile().getName());
            }
        } else if (getProjectName().isPresent()) {
            throw new GradleException("Project name is not supported for '" + initializer.getId() + "' build type.");
        }
        return "";
    }

    @VisibleForTesting
    String getEffectivePackageName(BuildInitializer initializer) {
        if (initializer.supportsPackage()) {
            return getPackageName().getOrElse(
                getProviderFactory().gradleProperty(SOURCE_PACKAGE_PROPERTY).getOrElse(SOURCE_PACKAGE_DEFAULT)
            );
        } else if (getPackageName().isPresent()) {
            throw new GradleException("Package name is not supported for '" + initializer.getId() + "' build type.");
        }
        return "";
    }

    private BuildInitializer getBuildInitializer(UserQuestions userQuestions, ProjectLayoutSetupRegistry projectLayoutRegistry) {
        if (getType().isPresent()) {
            return projectLayoutRegistry.get(getType().get());
        }

        BuildConverter converter = projectLayoutRegistry.getBuildConverter();
        if (converter.canApplyToCurrentDirectory(projectDir)) {
            if (userQuestions.askBooleanQuestion("Found a " + converter.getSourceBuildDescription() + " build. Generate a Gradle build from this?", true)) {
                return converter;
            }
        }
        return selectTypeOfBuild(userQuestions, projectLayoutRegistry);
    }

    private static BuildGenerator selectTypeOfBuild(UserQuestions userQuestions, ProjectLayoutSetupRegistry projectLayoutRegistry) {
        // Require that the default option is also the first option
        assert projectLayoutRegistry.getDefaultComponentType() == projectLayoutRegistry.getComponentTypes().get(0);

        ComponentType componentType = userQuestions.choice("Select type of build to generate", projectLayoutRegistry.getComponentTypes())
            .renderUsing(ComponentType::getDisplayName)
            .defaultOption(projectLayoutRegistry.getDefaultComponentType())
            .whenNotConnected(projectLayoutRegistry.getDefault().getComponentType())
            .ask();
        List<BuildGenerator> generators = projectLayoutRegistry.getGeneratorsFor(componentType);
        if (generators.size() == 1) {
            return generators.get(0);
        }

        Map<Language, BuildGenerator> generatorsByLanguage = new LinkedHashMap<>();
        for (Language language : Language.values()) {
            for (BuildGenerator generator : generators) {
                if (generator.productionCodeUses(language)) {
                    generatorsByLanguage.put(language, generator);
                    break;
                }
            }
        }
        Language language = userQuestions.choice("Select implementation language", generatorsByLanguage.keySet()).ask();
        return generatorsByLanguage.get(language);
    }

    @OptionValues("type")
    @ToBeReplacedByLazyProperty(comment = "Not yet supported", issue = "https://github.com/gradle/gradle/issues/29341")
    public List<String> getAvailableBuildTypes() {
        return getProjectLayoutRegistry().getAllTypes();
    }

    /**
     * Available build script DSLs to be used.
     *
     * @since 4.5
     */
    @OptionValues("dsl")
    @ToBeReplacedByLazyProperty(comment = "Not yet supported", issue = "https://github.com/gradle/gradle/issues/29341")
    public List<String> getAvailableDSLs() {
        return BuildInitDsl.listSupported();
    }

    /**
     * Available test frameworks.
     */
    @OptionValues("test-framework")
    @ToBeReplacedByLazyProperty(comment = "Not yet supported", issue = "https://github.com/gradle/gradle/issues/29341")
    public List<String> getAvailableTestFrameworks() {
        return BuildInitTestFramework.listSupported();
    }

    void setProjectLayoutRegistry(ProjectLayoutSetupRegistry projectLayoutRegistry) {
        this.projectLayoutRegistry = projectLayoutRegistry;
    }

    private String detectType() {
        if (getType().isPresent()) {
            return getType().get();
        }
        ProjectLayoutSetupRegistry projectLayoutRegistry = getProjectLayoutRegistry();
        BuildConverter buildConverter = projectLayoutRegistry.getBuildConverter();
        if (buildConverter.canApplyToCurrentDirectory(projectDir)) {
            return buildConverter.getId();
        }
        return projectLayoutRegistry.getDefault().getId();
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract UserInputHandler getUserInputHandler();

    @Inject
    protected abstract ProjectLayout getLayout();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract BuildInitSpecRegistry getBuildInitSpecRegistry();
}
