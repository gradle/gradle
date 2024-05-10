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
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.util.GradleVersion;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import java.io.File;
import java.util.Arrays;
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
    static final int MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API = 7;
    static final int DEFAULT_JAVA_VERSION = 21;

    private final Directory projectDir = getProject().getLayout().getProjectDirectory();
    private String type;
    private final Property<Boolean> splitProject = getProject().getObjects().property(Boolean.class);
    private String dsl;
    private final Property<Boolean> useIncubatingAPIs = getProject().getObjects().property(Boolean.class);
    private String testFramework;
    private String projectName;
    private String packageName;
    private final Property<InsecureProtocolOption> insecureProtocol = getProject().getObjects().property(InsecureProtocolOption.class);
    private final Property<String> javaVersion = getProject().getObjects().property(String.class);
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
     * This property can be set via command-line option '--type'.
     * <p>
     * Defaults to 'basic' - a minimal scaffolding, following Gradle best practices.
     * If a `pom.xml` is found in the project root directory, the type defaults to 'pom'
     * and the existing project is converted to Gradle.
     * <p>
     * Possible values for the option are provided by {@link #getAvailableBuildTypes()}.
     */
    @Input
    public String getType() {
        return isNullOrEmpty(type) ? detectType() : type;
    }

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
    public Property<Boolean> getSplitProject() {
        return splitProject;
    }

    /**
     * The desired DSL of build scripts to create, defaults to 'kotlin'.
     *
     * This property can be set via command-line option '--dsl'.
     *
     * @since 4.5
     */
    @Optional
    @Input
    public String getDsl() {
        return isNullOrEmpty(dsl) ? BuildInitDsl.KOTLIN.getId() : dsl;
    }

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
    public Property<Boolean> getUseIncubating() {
        return useIncubatingAPIs;
    }

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
    public Property<String> getJavaVersion() {
        return javaVersion;
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
        return projectName == null ? projectDir.getAsFile().getName() : projectName;
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
        return packageName == null ? "" : packageName;
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
        return testFramework;
    }

    /**
     * How to handle insecure (http) URLs used for Maven Repositories.
     *
     * This property can be set via command-line option '--insecure-protocol'.  The default value is 'warn'.
     *
     * @since 7.3
     */
    @Input
    @Option(option = "insecure-protocol", description = "How to handle insecure URLs used for Maven Repositories.")
    public Property<InsecureProtocolOption> getInsecureProtocol() {
        return insecureProtocol;
    }

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

    public ProjectLayoutSetupRegistry getProjectLayoutRegistry() {
        if (projectLayoutRegistry == null) {
            projectLayoutRegistry = getServices().get(ProjectLayoutSetupRegistry.class);
        }

        return projectLayoutRegistry;
    }

    @TaskAction
    public void setupProjectLayout() {
        UserInputHandler inputHandler = getEffectiveInputHandler();
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
            insecureProtocol.get(),
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
        return Objects.equals(getType(), "pom");
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

        String version = javaVersion.getOrNull();
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
        if (isNullOrEmpty(this.dsl)) {
            dsl = userQuestions.selectOption("Select build script DSL", initializer.getDsls(), initializer.getDefaultDsl());
        } else {
            dsl = BuildInitDsl.fromName(getDsl());
            if (!initializer.getDsls().contains(dsl)) {
                throw new GradleException("The requested DSL '" + getDsl() + "' is not supported for '" + initializer.getId() + "' build type");
            }
        }
        return dsl;
    }

    private ModularizationOption getModularizationOption(UserQuestions userQuestions, BuildInitializer initializer) {
        if (splitProject.isPresent()) {
            return splitProject.get() ? ModularizationOption.WITH_LIBRARY_PROJECTS : ModularizationOption.SINGLE_PROJECT;
        }
        return userQuestions.choice("Select application structure", initializer.getModularizationOptions())
            .renderUsing(ModularizationOption::getDisplayName)
            .ask();
    }

    private boolean shouldUseIncubatingAPIs(UserQuestions userQuestions) {
        if (this.useIncubatingAPIs.isPresent()) {
            return this.useIncubatingAPIs.get();
        }
        return userQuestions.askBooleanQuestion("Generate build using new APIs and behavior (some features may change in the next minor release)?", false);
    }

    private BuildInitTestFramework getBuildInitTestFramework(UserQuestions userQuestions, BuildInitializer initializer, ModularizationOption modularizationOption) {
        if (!isNullOrEmpty(this.testFramework)) {
            return initializer.getTestFrameworks(modularizationOption).stream()
                .filter(candidate -> this.testFramework.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> createNotSupportedTestFrameWorkException(initializer, modularizationOption));
        }

        BuildInitTestFramework testFramework = initializer.getDefaultTestFramework(modularizationOption);
        return userQuestions.selectOption("Select test framework", initializer.getTestFrameworks(modularizationOption), testFramework);
    }

    private GradleException createNotSupportedTestFrameWorkException(BuildInitializer initDescriptor, ModularizationOption modularizationOption) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("The requested test framework '" + getTestFramework() + "' is not supported for '" + initDescriptor.getId() + "' build type. Supported frameworks");
        formatter.startChildren();
        for (BuildInitTestFramework framework : initDescriptor.getTestFrameworks(modularizationOption)) {
            formatter.node("'" + framework.getId() + "'");
        }
        formatter.endChildren();
        return new GradleException(formatter.toString());
    }

    @VisibleForTesting
    String getEffectiveProjectName(UserQuestions userQuestions, BuildInitializer initializer) {
        String projectName = this.projectName;
        if (initializer.supportsProjectName()) {
            if (isNullOrEmpty(projectName)) {
                return userQuestions.askQuestion("Project name", getProjectName());
            }
        } else if (!isNullOrEmpty(projectName)) {
            throw new GradleException("Project name is not supported for '" + initializer.getId() + "' build type.");
        }
        return projectName;
    }

    @VisibleForTesting
    String getEffectivePackageName(BuildInitializer initializer) {
        String packageName = this.packageName;
        if (initializer.supportsPackage()) {
            if (packageName == null) {
                return getProviderFactory().gradleProperty(SOURCE_PACKAGE_PROPERTY).getOrElse(SOURCE_PACKAGE_DEFAULT);
            }
        } else if (!isNullOrEmpty(packageName)) {
            throw new GradleException("Package name is not supported for '" + initializer.getId() + "' build type.");
        }
        return packageName;
    }

    private BuildInitializer getBuildInitializer(UserQuestions userQuestions, ProjectLayoutSetupRegistry projectLayoutRegistry) {
        if (!isNullOrEmpty(type)) {
            return projectLayoutRegistry.get(type);
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

    @Option(option = "type", description = "Set the type of project to generate.")
    public void setType(String type) {
        this.type = type;
    }

    @OptionValues("type")
    public List<String> getAvailableBuildTypes() {
        return getProjectLayoutRegistry().getAllTypes();
    }

    /**
     * Set the build script DSL to be used.
     *
     * @since 4.5
     */
    @Option(option = "dsl", description = "Set the build script DSL to be used in generated scripts.")
    public void setDsl(String dsl) {
        this.dsl = dsl;
    }

    /**
     * Available build script DSLs to be used.
     *
     * @since 4.5
     */
    @OptionValues("dsl")
    public List<String> getAvailableDSLs() {
        return BuildInitDsl.listSupported();
    }

    /**
     * Set the test framework to be used.
     */
    @Option(option = "test-framework", description = "Set the test framework to be used.")
    public void setTestFramework(@Nullable String testFramework) {
        this.testFramework = testFramework;
    }

    /**
     * Available test frameworks.
     */
    @OptionValues("test-framework")
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
        this.projectName = projectName;
    }

    /**
     * Set the package name.
     *
     * @since 5.0
     */
    @Option(option = "package", description = "Set the package for source files.")
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    void setProjectLayoutRegistry(ProjectLayoutSetupRegistry projectLayoutRegistry) {
        this.projectLayoutRegistry = projectLayoutRegistry;
    }

    private String detectType() {
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
}
