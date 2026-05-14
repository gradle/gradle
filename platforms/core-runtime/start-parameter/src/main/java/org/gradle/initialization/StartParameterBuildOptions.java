/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization;

import com.google.common.base.Splitter;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.cli.OptionCategory;
import org.gradle.internal.buildoption.BooleanBuildOption;
import org.gradle.internal.buildoption.BooleanCommandLineOptionConfiguration;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.BuildOptionSet;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.EnabledOnlyBooleanBuildOption;
import org.gradle.internal.buildoption.EnumBuildOption;
import org.gradle.internal.buildoption.IntegerBuildOption;
import org.gradle.internal.buildoption.ListBuildOption;
import org.gradle.internal.buildoption.Origin;
import org.gradle.internal.buildoption.StringBuildOption;
import org.gradle.internal.invocation.parameters.ConfigurationCacheProblemsMode;
import org.gradle.internal.watch.registry.WatchMode;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StartParameterBuildOptions extends BuildOptionSet<StartParameterBuildOptions.ParsedOptions> {

    private static final List<BuildOption<ParsedOptions>> OPTIONS = Arrays.asList(
        new ProjectCacheDirOption(),
        new RerunTasksOption(),
        new ProfileOption(),
        new ContinueOption(),
        new OfflineOption(),
        new RefreshDependenciesOption(),
        new DryRunOption(),
        new ContinuousOption(),
        new ContinuousBuildQuietPeriodOption(),
        new NoProjectDependenciesRebuildOption(),
        new InitScriptOption(),
        new ExcludeTaskOption(),
        new IncludeBuildOption(),
        new ConfigureOnDemandOption(),
        new BuildCacheOption(),
        new BuildCacheDebugLoggingOption(),
        new WatchFileSystemOption(),
        new VfsVerboseLoggingOption(),
        new BuildScanOption(),
        new DevelocityUrlOption(),
        new DevelocityPluginVersionOption(),
        new DependencyLockingWriteOption(),
        new DependencyVerificationWriteOption(),
        new DependencyVerificationModeOption(),
        new DependencyLockingUpdateOption(),
        new RefreshKeysOption(),
        new ExportKeysOption(),
        new ConfigurationCacheProblemsOption(),
        new ConfigurationCacheOption(),
        new ConfigurationCacheIgnoreInputsDuringStore(),
        new ConfigurationCacheIgnoreUnsupportedBuildEventsListeners(),
        new ConfigurationCacheMaxProblemsOption(),
        new ConfigurationCacheIgnoredFileSystemCheckInputs(),
        new ConfigurationCacheDebugOption(),
        new ConfigurationCacheParallelOption(),
        new ConfigurationCacheReadOnlyOption(),
        new ConfigurationCacheRecreateOption(),
        new ConfigurationCacheQuietOption(),
        new ConfigurationCacheIntegrityCheckOption(),
        new ConfigurationCacheEntriesPerKeyOption(),
        new ConfigurationCacheHeapDumpDir(),
        new ConfigurationCacheFineGrainedPropertyTracking(),
        new IsolatedProjectsOption(),
        new ProblemReportGenerationOption(),
        new PropertyUpgradeReportOption(),
        new TaskGraphOption(),
        new ParallelToolingModelBuildingOption()
    );

    @Override
    public List<? extends BuildOption<? super ParsedOptions>> getAllOptions() {
        return OPTIONS;
    }

    public static class ProjectCacheDirOption extends StringBuildOption<ParsedOptions> {
        public static final String PROPERTY_NAME = "org.gradle.projectcachedir";

        public ProjectCacheDirOption() {
            super(PROPERTY_NAME, CommandLineOptionConfiguration.create("project-cache-dir", "Specifies the project-specific cache directory. Default is .gradle in the root project directory."));
        }

        @Override
        public void applyTo(String value, ParsedOptions settings, Origin origin) {
            settings.setProjectCacheDir(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    public static class RerunTasksOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {
        public RerunTasksOption() {
            super(null, CommandLineOptionConfiguration.create("rerun-tasks", "Ignores previously cached task results."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setRerunTasks(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.EXECUTION;
        }
    }

    public static class ProfileOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {
        public ProfileOption() {
            super(null, CommandLineOptionConfiguration.create("profile", "Profiles build execution time. Generates a report in the <build_dir>/reports/profile directory."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setProfile(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.DIAGNOSTICS;
        }
    }

    public static class ContinueOption extends BooleanBuildOption<ParsedOptions> {
        public static final String LONG_OPTION = "continue";

        public static final String PROPERTY_NAME = "org.gradle.continue";

        public ContinueOption() {
            super(
                PROPERTY_NAME,
                BooleanCommandLineOptionConfiguration.create(
                    LONG_OPTION,
                    "Continues task execution after a task failure.",
                    "Stops task execution after a task failure."
                )
            );
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setContinueOnFailure(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.EXECUTION;
        }
    }

    public static class OfflineOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {
        public OfflineOption() {
            super(null, CommandLineOptionConfiguration.create("offline", "Runs the build without accessing network resources."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setOffline(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    public static class RefreshDependenciesOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {
        public RefreshDependenciesOption() {
            super(null, CommandLineOptionConfiguration.create("refresh-dependencies", "U", "Refreshes the state of dependencies."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setRefreshDependencies(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    public static class DryRunOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {
        public DryRunOption() {
            super(null, CommandLineOptionConfiguration.create("dry-run", "m", "Runs the build with all task actions disabled."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setDryRun(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.EXECUTION;
        }
    }

    public static class ContinuousOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {
        public ContinuousOption() {
            super(null, CommandLineOptionConfiguration.create("continuous", "t", "Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setContinuous(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.EXECUTION;
        }
    }

    public static class ContinuousBuildQuietPeriodOption extends IntegerBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.continuous.quietperiod";

        public ContinuousBuildQuietPeriodOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(int quietPeriodMillis, ParsedOptions settings, Origin origin) {
            settings.setContinuousBuildQuietPeriod(Duration.ofMillis(quietPeriodMillis));
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.PERFORMANCE;
        }
    }

    public static class NoProjectDependenciesRebuildOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {
        private static final String LONG_OPTION = "no-rebuild";
        private static final String SHORT_OPTION = "a";

        public NoProjectDependenciesRebuildOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Disables rebuilding of project dependencies."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setBuildProjectDependencies(Boolean.FALSE);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.EXECUTION;
        }
    }

    public static class InitScriptOption extends ListBuildOption<ParsedOptions> {
        public InitScriptOption() {
            super(null, CommandLineOptionConfiguration.create("init-script", "I", "Specifies an initialization script."));
        }

        @Override
        public void applyTo(List<String> values, ParsedOptions settings, Origin origin) {
            settings.setInitScripts(values);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    public static class ExcludeTaskOption extends ListBuildOption<ParsedOptions> {
        public ExcludeTaskOption() {
            super(null, CommandLineOptionConfiguration.create("exclude-task", "x", "Specifies a task to exclude from execution."));
        }

        @Override
        public void applyTo(List<String> values, ParsedOptions settings, Origin origin) {
            settings.setExcludedTaskNames(values);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.EXECUTION;
        }
    }

    public static class IncludeBuildOption extends ListBuildOption<ParsedOptions> {
        public IncludeBuildOption() {
            super(null, CommandLineOptionConfiguration.create("include-build", "Includes the specified build in the composite."));
        }

        @Override
        public void applyTo(List<String> values, ParsedOptions settings, Origin origin) {
            settings.setIncludedBuilds(values);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    public static class ConfigureOnDemandOption extends BooleanBuildOption<ParsedOptions> {
        public static final String GRADLE_PROPERTY = "org.gradle.configureondemand";

        public ConfigureOnDemandOption() {
            super(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create("configure-on-demand", "Configures necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.", "Disables the use of configuration on demand.").incubating());
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigureOnDemand(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.PERFORMANCE;
        }
    }

    public static class BuildCacheOption extends BooleanBuildOption<ParsedOptions> {
        public static final String GRADLE_PROPERTY = "org.gradle.caching";

        public BuildCacheOption() {
            super(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create("build-cache", "Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.", "Disables the Gradle build cache."));
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setBuildCacheEnabled(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.PERFORMANCE;
        }
    }

    public static class BuildCacheDebugLoggingOption extends BooleanBuildOption<ParsedOptions> {
        public static final String GRADLE_PROPERTY = "org.gradle.caching.debug";

        public BuildCacheDebugLoggingOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setBuildCacheDebugLogging(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.LOGGING;
        }
    }

    public static class WatchFileSystemOption extends BooleanBuildOption<ParsedOptions> {
        public static final String LONG_OPTION = "watch-fs";
        public static final String GRADLE_PROPERTY = "org.gradle.vfs.watch";

        public WatchFileSystemOption() {
            super(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create(
                LONG_OPTION,
                "Enables file system watching. Reuses file system data for subsequent builds.",
                "Disables file system watching."
            ));
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setWatchFileSystemMode(value
                ? WatchMode.ENABLED
                : WatchMode.DISABLED
            );
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.PERFORMANCE;
        }
    }

    public static class VfsVerboseLoggingOption extends BooleanBuildOption<ParsedOptions> {
        public static final String GRADLE_PROPERTY = "org.gradle.vfs.verbose";

        public VfsVerboseLoggingOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions startParameter, Origin origin) {
            startParameter.setVfsVerboseLogging(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.LOGGING;
        }
    }

    public static class BuildScanOption extends BooleanBuildOption<ParsedOptions> {
        public static final String LONG_OPTION = "scan";

        public BuildScanOption() {
            super(null, BooleanCommandLineOptionConfiguration.create(LONG_OPTION,
                "Generates a Build Scan (powered by Develocity).",
                "Disables the creation of a Build Scan."));
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setBuildScan(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.DEVELOCITY;
        }
    }

    public static class DevelocityUrlOption extends StringBuildOption<ParsedOptions> {
        public static final String LONG_OPTION = "develocity-url";
        public static final String GRADLE_PROPERTY = "com.gradle.develocity.url";
        public static final String ENVIRONMENT_VARIABLE = "COM_GRADLE_DEVELOCITY_URL";

        public DevelocityUrlOption() {
            super(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION,
                "Default URL of the Develocity server to publish Build Scan to. Triggers auto-application of the Develocity plugin if not already applied.\n" +
                    "Has no effect if the Develocity plugin is already applied and a server URL is configured."));
        }

        @Override
        public void applyTo(String value, ParsedOptions settings, Origin origin) {
            settings.setDevelocityUrl(value);
        }

        @Override
        public void applyFromEnvVar(Map<String, String> envVars, ParsedOptions settings) {
            String develocityUrlEnvVar = envVars.get(ENVIRONMENT_VARIABLE);
            if (develocityUrlEnvVar != null) {
                settings.setDevelocityUrl(develocityUrlEnvVar);
            }
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.DEVELOCITY;
        }
    }

    public static class DevelocityPluginVersionOption extends StringBuildOption<ParsedOptions> {
        public static final String LONG_OPTION = "develocity-plugin-version";
        public static final String GRADLE_PROPERTY = "com.gradle.develocity.plugin.version";
        public static final String ENVIRONMENT_VARIABLE = "COM_GRADLE_DEVELOCITY_PLUGIN_VERSION";

        public DevelocityPluginVersionOption() {
            super(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION,
                "Version of the Develocity plugin to auto-apply, must be 4.4.0 or higher if Develocity URL is specified as well.\n" +
                    "Used only if --develocity-url or --scan triggers auto-application of the Develocity plugin."));
        }

        @Override
        public void applyTo(String value, ParsedOptions settings, Origin origin) {
            settings.setDevelocityPluginVersion(value);
        }

        @Override
        public void applyFromEnvVar(Map<String, String> envVars, ParsedOptions settings) {
            String develocityPluginVersionEnvVar = envVars.get(ENVIRONMENT_VARIABLE);
            if (develocityPluginVersionEnvVar != null) {
                settings.setDevelocityPluginVersion(develocityPluginVersionEnvVar);
            }
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.DEVELOCITY;
        }
    }

    public static class DependencyLockingWriteOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {
        public static final String LONG_OPTION = "write-locks";

        public DependencyLockingWriteOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, "Persists dependency resolution for locked configurations. Ignores existing locking information if it exists."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setWriteDependencyLocks(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.SECURITY;
        }
    }

    public static class DependencyVerificationWriteOption extends StringBuildOption<ParsedOptions> {
        public static final String SHORT_OPTION = "M";
        public static final String LONG_OPTION = "write-verification-metadata";

        DependencyVerificationWriteOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION,
                "Generates checksums for dependencies used in the project. Accepts a comma-separated list."));
        }

        @Override
        public void applyTo(String value, ParsedOptions settings, Origin origin) {
            List<String> checksums = Splitter.on(",")
                .omitEmptyStrings()
                .trimResults()
                .splitToList(value)
                .stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
            settings.setWriteDependencyVerifications(checksums);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.SECURITY;
        }
    }

    public static class DependencyVerificationModeOption extends EnumBuildOption<DependencyVerificationMode, ParsedOptions> {

        private static final String GRADLE_PROPERTY = "org.gradle.dependency.verification";
        private static final String LONG_OPTION = "dependency-verification";
        private static final String SHORT_OPTION = "F";

        public DependencyVerificationModeOption() {
            super(LONG_OPTION,
                DependencyVerificationMode.class,
                DependencyVerificationMode.values(),
                GRADLE_PROPERTY,
                CommandLineOptionConfiguration.create(
                    LONG_OPTION, SHORT_OPTION, "Configures the dependency verification mode. Supported values are 'strict', 'lenient', or 'off'.")
            );
        }

        @Override
        public void applyTo(DependencyVerificationMode value, ParsedOptions settings, Origin origin) {
            settings.setDependencyVerificationMode(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.SECURITY;
        }
    }

    public static class DependencyLockingUpdateOption extends ListBuildOption<ParsedOptions> {

        public DependencyLockingUpdateOption() {
            super(null, CommandLineOptionConfiguration.create("update-locks", "Performs a partial update of the dependency lock. Allows passed-in module notations to change version.").incubating());
        }

        @Override
        public void applyTo(List<String> modulesToUpdate, ParsedOptions settings, Origin origin) {
            settings.setLockedDependenciesToUpdate(modulesToUpdate);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.SECURITY;
        }
    }

    public static class RefreshKeysOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {

        private static final String LONG_OPTION = "refresh-keys";

        public RefreshKeysOption() {
            super(null,
                CommandLineOptionConfiguration.create(LONG_OPTION, "Refreshes the public keys used for dependency verification."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setRefreshKeys(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.SECURITY;
        }
    }

    public static class ExportKeysOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {

        public static final String LONG_OPTION = "export-keys";

        public ExportKeysOption() {
            super(null,
                CommandLineOptionConfiguration.create(LONG_OPTION, "Exports the public keys used for dependency verification."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setExportKeys(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.SECURITY;
        }
    }

    public static class ConfigurationCacheOption extends BooleanBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.configuration-cache";
        public static final String DEPRECATED_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache";
        public static final String LONG_OPTION = "configuration-cache";

        public ConfigurationCacheOption() {
            super(
                PROPERTY_NAME,
                DEPRECATED_PROPERTY_NAME,
                BooleanCommandLineOptionConfiguration.create(
                    LONG_OPTION,
                    "Enables the configuration cache. Gradle will try to reuse the build configuration from previous builds.",
                    "Disables the configuration cache."
                )
            );
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCache(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.PERFORMANCE;
        }
    }

    public static class IsolatedProjectsOption extends BooleanBuildOption<ParsedOptions> {
        public static final String PROPERTY_NAME = "org.gradle.unsafe.isolated-projects";

        public IsolatedProjectsOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setIsolatedProjects(value);
        }
    }

    public static class ConfigurationCacheProblemsOption extends EnumBuildOption<ConfigurationCacheProblemsMode, ParsedOptions> {
        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.problems";
        public static final String DEPRECATED_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache-problems";
        public static final String LONG_OPTION = "configuration-cache-problems";

        public ConfigurationCacheProblemsOption() {
            super(
                LONG_OPTION,
                ConfigurationCacheProblemsMode.class,
                ConfigurationCacheProblemsMode.values(),
                PROPERTY_NAME,
                DEPRECATED_PROPERTY_NAME,
                CommandLineOptionConfiguration.create(
                    LONG_OPTION,
                    "Configures how the configuration cache handles problems (fail or warn). Supported values are 'warn', or 'fail' (default)."
                )
            );
        }

        @Override
        public void applyTo(ConfigurationCacheProblemsMode value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheProblems(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.DIAGNOSTICS;
        }
    }

    public static class ConfigurationCacheIgnoreInputsDuringStore extends BooleanBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.inputs.unsafe.ignore.in-serialization";

        public ConfigurationCacheIgnoreInputsDuringStore() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheIgnoreInputsDuringStore(value);
        }
    }

    /**
     * Suppresses Configuration Cache problems for unsupported listeners registered in {@code BuildEventsListenersRegistry}.
     *
     * @since 9.0.0
     */
    public static class ConfigurationCacheIgnoreUnsupportedBuildEventsListeners extends BooleanBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.unsafe.ignore.unsupported-build-events-listeners";

        public ConfigurationCacheIgnoreUnsupportedBuildEventsListeners() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(value);
        }
    }

    public static class ConfigurationCacheMaxProblemsOption extends IntegerBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.max-problems";

        public static final String DEPRECATED_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache.max-problems";

        public ConfigurationCacheMaxProblemsOption() {
            super(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME);
        }

        @Override
        public void applyTo(int value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheMaxProblems(value);
        }

    }

    public static class ConfigurationCacheIgnoredFileSystemCheckInputs extends StringBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.inputs.unsafe.ignore.file-system-checks";

        public ConfigurationCacheIgnoredFileSystemCheckInputs() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(String value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheIgnoredFileSystemCheckInputs(value);
        }
    }

    public static class ConfigurationCacheDebugOption extends BooleanBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.internal.configuration-cache.debug";
        public static final String DEPRECATED_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache.debug";

        public ConfigurationCacheDebugOption() {
            super(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheDebug(value);
        }
    }

    public static class ConfigurationCacheParallelOption extends BooleanBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.parallel";

        public ConfigurationCacheParallelOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheParallel(value);
        }
    }

    public static class ConfigurationCacheReadOnlyOption extends BooleanBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.read-only";

        public ConfigurationCacheReadOnlyOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheReadOnly(value);
        }
    }

    public static class ConfigurationCacheEntriesPerKeyOption extends IntegerBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.entries-per-key";

        public ConfigurationCacheEntriesPerKeyOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(int value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheEntriesPerKey(value);
        }
    }

    public static class ConfigurationCacheRecreateOption extends BooleanBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.internal.configuration-cache.recreate-cache";
        public static final String DEPRECATED_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache.recreate-cache";

        public ConfigurationCacheRecreateOption() {
            super(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheRecreateCache(value);
        }

    }

    public static class ConfigurationCacheQuietOption extends BooleanBuildOption<ParsedOptions> {

        public static final String PROPERTY_NAME = "org.gradle.internal.configuration-cache.quiet";
        public static final String DEPRECATED_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache.quiet";

        public ConfigurationCacheQuietOption() {
            super(PROPERTY_NAME, DEPRECATED_PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheQuiet(value);
        }
    }

    /**
     * Enables stricter integrity checks of the stored configuration cache entries, at the cost of potential performance penalty and significantly inflated entry size.
     * Can be useful when debugging store failures.
     */
    public static class ConfigurationCacheIntegrityCheckOption extends BooleanBuildOption<ParsedOptions> {
        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.integrity-check";

        public ConfigurationCacheIntegrityCheckOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheIntegrityCheckEnabled(value);
        }
    }

    /**
     * When set, tells Gradle to emit heap dumps in the given directory after loading the work graph on a Configuration Cache hit,
     * after storing and loading the work graph on a Configuration Cache miss.
     */
    public static class ConfigurationCacheHeapDumpDir extends StringBuildOption<ParsedOptions> {
        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.heap-dump-dir";

        public ConfigurationCacheHeapDumpDir() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(String value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheHeapDumpDir(value);
        }
    }

    /**
     * Whether [project property accesses][org.gradle.api.internal.properties.GradleProperties] are tracked individually
     * to increase cache hit rates.
     *
     * Increases memory usage proportionally to the number of projects and property accesses.
     *
     * It can be disabled to save on memory.
     *
     * The default is `true`.
     */
    public static class ConfigurationCacheFineGrainedPropertyTracking extends BooleanBuildOption<ParsedOptions> {
        public static final String PROPERTY_NAME = "org.gradle.configuration-cache.fine-grained-property-tracking";

        public ConfigurationCacheFineGrainedPropertyTracking() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.setConfigurationCacheFineGrainedPropertyTracking(value);
        }
    }

    public static class PropertyUpgradeReportOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {

        public static final String LONG_OPTION = "property-upgrade-report";

        public PropertyUpgradeReportOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, "Runs the build with the experimental property upgrade report.").incubating());
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setPropertyUpgradeReportEnabled(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.DIAGNOSTICS;
        }
    }

    public static class ProblemReportGenerationOption extends BooleanBuildOption<ParsedOptions> {

        public static final String LONG_OPTION = "problems-report";
        public static final String GRADLE_PROPERTY = "org.gradle.problems.report";

        public ProblemReportGenerationOption() {
            super(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create(LONG_OPTION, "Enables the HTML problems report.", "Disables the HTML problems report.").incubating());
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, Origin origin) {
            settings.enableProblemReportGeneration(value);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.DIAGNOSTICS;
        }
    }

    public static class TaskGraphOption extends EnabledOnlyBooleanBuildOption<ParsedOptions> {

        public static final String LONG_OPTION = "task-graph";

        public TaskGraphOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, "Prints the task graph instead of executing tasks."));
        }

        @Override
        public void applyTo(ParsedOptions settings, Origin origin) {
            settings.setTaskGraph(true);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.DIAGNOSTICS;
        }
    }

    public static class ParallelToolingModelBuildingOption extends BooleanBuildOption<ParsedOptions> {
        public static final String PROPERTY_NAME = "org.gradle.tooling.parallel";

        public ParallelToolingModelBuildingOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, ParsedOptions settings, @Nullable Origin origin) {
            settings.setParallelToolingModelBuilding(value);
        }
    }

    public static class ParsedOptions {
        private @Nullable String projectCacheDir;
        private @Nullable Boolean rerunTasks;
        private @Nullable Boolean profile;
        private @Nullable Boolean continueOnFailure;
        private @Nullable Boolean offline;
        private @Nullable Boolean refreshDependencies;
        private @Nullable Boolean dryRun;
        private @Nullable Boolean continuous;
        private @Nullable Duration continuousBuildQuietPeriod;
        private @Nullable Boolean buildProjectDependencies;
        private @Nullable List<String> initScripts;
        private @Nullable List<String> excludedTaskNames;
        private @Nullable List<String> includedBuilds;
        private @Nullable Boolean configureOnDemand;
        private @Nullable Boolean buildCacheEnabled;
        private @Nullable Boolean buildCacheDebugLogging;
        private @Nullable WatchMode watchFileSystemMode;
        private @Nullable Boolean vfsVerboseLogging;
        private @Nullable Boolean buildScan;
        private @Nullable String develocityUrl;
        private @Nullable String develocityPluginVersion;
        private @Nullable Boolean writeDependencyLocks;
        private @Nullable List<String> writeDependencyVerifications;
        private @Nullable DependencyVerificationMode dependencyVerificationMode;
        private @Nullable List<String> lockedDependenciesToUpdate;
        private @Nullable Boolean refreshKeys;
        private @Nullable Boolean exportKeys;
        private @Nullable ConfigurationCacheProblemsMode configurationCacheProblems;
        private @Nullable Boolean configurationCache;
        private @Nullable Boolean configurationCacheIgnoreInputsDuringStore;
        private @Nullable Boolean configurationCacheIgnoreUnsupportedBuildEventsListeners;
        private @Nullable Integer configurationCacheMaxProblems;
        private @Nullable String configurationCacheIgnoredFileSystemCheckInputs;
        private @Nullable Boolean configurationCacheDebug;
        private @Nullable Boolean configurationCacheParallel;
        private @Nullable Boolean configurationCacheReadOnly;
        private @Nullable Boolean configurationCacheRecreateCache;
        private @Nullable Boolean configurationCacheQuiet;
        private @Nullable Boolean configurationCacheIntegrityCheckEnabled;
        private @Nullable Integer configurationCacheEntriesPerKey;
        private @Nullable String configurationCacheHeapDumpDir;
        private @Nullable Boolean configurationCacheFineGrainedPropertyTracking;
        private @Nullable Boolean isolatedProjects;
        private @Nullable Boolean problemReportGeneration;
        private @Nullable Boolean propertyUpgradeReportEnabled;
        private @Nullable Boolean taskGraph;
        private @Nullable Boolean parallelToolingModelBuilding;

        public @Nullable String getProjectCacheDir() {
            return projectCacheDir;
        }

        public void setProjectCacheDir(@Nullable String projectCacheDir) {
            this.projectCacheDir = projectCacheDir;
        }

        public @Nullable Boolean getRerunTasks() {
            return rerunTasks;
        }

        public void setRerunTasks(@Nullable Boolean rerunTasks) {
            this.rerunTasks = rerunTasks;
        }

        public @Nullable Boolean getProfile() {
            return profile;
        }

        public void setProfile(@Nullable Boolean profile) {
            this.profile = profile;
        }

        public @Nullable Boolean getContinueOnFailure() {
            return continueOnFailure;
        }

        public void setContinueOnFailure(@Nullable Boolean continueOnFailure) {
            this.continueOnFailure = continueOnFailure;
        }

        public @Nullable Boolean getOffline() {
            return offline;
        }

        public void setOffline(@Nullable Boolean offline) {
            this.offline = offline;
        }

        public @Nullable Boolean getRefreshDependencies() {
            return refreshDependencies;
        }

        public void setRefreshDependencies(@Nullable Boolean refreshDependencies) {
            this.refreshDependencies = refreshDependencies;
        }

        public @Nullable Boolean getDryRun() {
            return dryRun;
        }

        public void setDryRun(@Nullable Boolean dryRun) {
            this.dryRun = dryRun;
        }

        public @Nullable Boolean getContinuous() {
            return continuous;
        }

        public void setContinuous(@Nullable Boolean continuous) {
            this.continuous = continuous;
        }

        public @Nullable Duration getContinuousBuildQuietPeriod() {
            return continuousBuildQuietPeriod;
        }

        public void setContinuousBuildQuietPeriod(@Nullable Duration continuousBuildQuietPeriod) {
            this.continuousBuildQuietPeriod = continuousBuildQuietPeriod;
        }

        public @Nullable Boolean getBuildProjectDependencies() {
            return buildProjectDependencies;
        }

        public void setBuildProjectDependencies(@Nullable Boolean buildProjectDependencies) {
            this.buildProjectDependencies = buildProjectDependencies;
        }

        public @Nullable List<String> getInitScripts() {
            return initScripts;
        }

        public void setInitScripts(@Nullable List<String> initScripts) {
            this.initScripts = initScripts;
        }

        public @Nullable List<String> getExcludedTaskNames() {
            return excludedTaskNames;
        }

        public void setExcludedTaskNames(@Nullable List<String> excludedTaskNames) {
            this.excludedTaskNames = excludedTaskNames;
        }

        public @Nullable List<String> getIncludedBuilds() {
            return includedBuilds;
        }

        public void setIncludedBuilds(@Nullable List<String> includedBuilds) {
            this.includedBuilds = includedBuilds;
        }

        public @Nullable Boolean getConfigureOnDemand() {
            return configureOnDemand;
        }

        public void setConfigureOnDemand(@Nullable Boolean configureOnDemand) {
            this.configureOnDemand = configureOnDemand;
        }

        public @Nullable Boolean getBuildCacheEnabled() {
            return buildCacheEnabled;
        }

        public void setBuildCacheEnabled(@Nullable Boolean buildCacheEnabled) {
            this.buildCacheEnabled = buildCacheEnabled;
        }

        public @Nullable Boolean getBuildCacheDebugLogging() {
            return buildCacheDebugLogging;
        }

        public void setBuildCacheDebugLogging(@Nullable Boolean buildCacheDebugLogging) {
            this.buildCacheDebugLogging = buildCacheDebugLogging;
        }

        public @Nullable WatchMode getWatchFileSystemMode() {
            return watchFileSystemMode;
        }

        public void setWatchFileSystemMode(@Nullable WatchMode watchFileSystemMode) {
            this.watchFileSystemMode = watchFileSystemMode;
        }

        public @Nullable Boolean getVfsVerboseLogging() {
            return vfsVerboseLogging;
        }

        public void setVfsVerboseLogging(@Nullable Boolean vfsVerboseLogging) {
            this.vfsVerboseLogging = vfsVerboseLogging;
        }

        public @Nullable Boolean getBuildScan() {
            return buildScan;
        }

        public void setBuildScan(@Nullable Boolean buildScan) {
            this.buildScan = buildScan;
        }

        public @Nullable String getDevelocityUrl() {
            return develocityUrl;
        }

        public void setDevelocityUrl(@Nullable String develocityUrl) {
            this.develocityUrl = develocityUrl;
        }

        public @Nullable String getDevelocityPluginVersion() {
            return develocityPluginVersion;
        }

        public void setDevelocityPluginVersion(@Nullable String develocityPluginVersion) {
            this.develocityPluginVersion = develocityPluginVersion;
        }

        public @Nullable Boolean getWriteDependencyLocks() {
            return writeDependencyLocks;
        }

        public void setWriteDependencyLocks(@Nullable Boolean writeDependencyLocks) {
            this.writeDependencyLocks = writeDependencyLocks;
        }

        public @Nullable List<String> getWriteDependencyVerifications() {
            return writeDependencyVerifications;
        }

        public void setWriteDependencyVerifications(@Nullable List<String> writeDependencyVerifications) {
            this.writeDependencyVerifications = writeDependencyVerifications;
        }

        public @Nullable DependencyVerificationMode getDependencyVerificationMode() {
            return dependencyVerificationMode;
        }

        public void setDependencyVerificationMode(@Nullable DependencyVerificationMode dependencyVerificationMode) {
            this.dependencyVerificationMode = dependencyVerificationMode;
        }

        public @Nullable List<String> getLockedDependenciesToUpdate() {
            return lockedDependenciesToUpdate;
        }

        public void setLockedDependenciesToUpdate(@Nullable List<String> lockedDependenciesToUpdate) {
            this.lockedDependenciesToUpdate = lockedDependenciesToUpdate;
        }

        public @Nullable Boolean getRefreshKeys() {
            return refreshKeys;
        }

        public void setRefreshKeys(@Nullable Boolean refreshKeys) {
            this.refreshKeys = refreshKeys;
        }

        public @Nullable Boolean getExportKeys() {
            return exportKeys;
        }

        public void setExportKeys(@Nullable Boolean exportKeys) {
            this.exportKeys = exportKeys;
        }

        public @Nullable ConfigurationCacheProblemsMode getConfigurationCacheProblems() {
            return configurationCacheProblems;
        }

        public void setConfigurationCacheProblems(@Nullable ConfigurationCacheProblemsMode configurationCacheProblems) {
            this.configurationCacheProblems = configurationCacheProblems;
        }

        public @Nullable Boolean getConfigurationCache() {
            return configurationCache;
        }

        public void setConfigurationCache(@Nullable Boolean configurationCache) {
            this.configurationCache = configurationCache;
        }

        public @Nullable Boolean getConfigurationCacheIgnoreInputsDuringStore() {
            return configurationCacheIgnoreInputsDuringStore;
        }

        public void setConfigurationCacheIgnoreInputsDuringStore(@Nullable Boolean configurationCacheIgnoreInputsDuringStore) {
            this.configurationCacheIgnoreInputsDuringStore = configurationCacheIgnoreInputsDuringStore;
        }

        public @Nullable Boolean getConfigurationCacheIgnoreUnsupportedBuildEventsListeners() {
            return configurationCacheIgnoreUnsupportedBuildEventsListeners;
        }

        public void setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(@Nullable Boolean configurationCacheIgnoreUnsupportedBuildEventsListeners) {
            this.configurationCacheIgnoreUnsupportedBuildEventsListeners = configurationCacheIgnoreUnsupportedBuildEventsListeners;
        }

        public @Nullable Integer getConfigurationCacheMaxProblems() {
            return configurationCacheMaxProblems;
        }

        public void setConfigurationCacheMaxProblems(@Nullable Integer configurationCacheMaxProblems) {
            this.configurationCacheMaxProblems = configurationCacheMaxProblems;
        }

        public @Nullable String getConfigurationCacheIgnoredFileSystemCheckInputs() {
            return configurationCacheIgnoredFileSystemCheckInputs;
        }

        public void setConfigurationCacheIgnoredFileSystemCheckInputs(@Nullable String configurationCacheIgnoredFileSystemCheckInputs) {
            this.configurationCacheIgnoredFileSystemCheckInputs = configurationCacheIgnoredFileSystemCheckInputs;
        }

        public @Nullable Boolean getConfigurationCacheDebug() {
            return configurationCacheDebug;
        }

        public void setConfigurationCacheDebug(@Nullable Boolean configurationCacheDebug) {
            this.configurationCacheDebug = configurationCacheDebug;
        }

        public @Nullable Boolean getConfigurationCacheParallel() {
            return configurationCacheParallel;
        }

        public void setConfigurationCacheParallel(@Nullable Boolean configurationCacheParallel) {
            this.configurationCacheParallel = configurationCacheParallel;
        }

        public @Nullable Boolean getConfigurationCacheReadOnly() {
            return configurationCacheReadOnly;
        }

        public void setConfigurationCacheReadOnly(@Nullable Boolean configurationCacheReadOnly) {
            this.configurationCacheReadOnly = configurationCacheReadOnly;
        }

        public @Nullable Boolean getConfigurationCacheRecreateCache() {
            return configurationCacheRecreateCache;
        }

        public void setConfigurationCacheRecreateCache(@Nullable Boolean configurationCacheRecreateCache) {
            this.configurationCacheRecreateCache = configurationCacheRecreateCache;
        }

        public @Nullable Boolean getConfigurationCacheQuiet() {
            return configurationCacheQuiet;
        }

        public void setConfigurationCacheQuiet(@Nullable Boolean configurationCacheQuiet) {
            this.configurationCacheQuiet = configurationCacheQuiet;
        }

        public @Nullable Boolean getConfigurationCacheIntegrityCheckEnabled() {
            return configurationCacheIntegrityCheckEnabled;
        }

        public void setConfigurationCacheIntegrityCheckEnabled(@Nullable Boolean configurationCacheIntegrityCheckEnabled) {
            this.configurationCacheIntegrityCheckEnabled = configurationCacheIntegrityCheckEnabled;
        }

        public @Nullable Integer getConfigurationCacheEntriesPerKey() {
            return configurationCacheEntriesPerKey;
        }

        public void setConfigurationCacheEntriesPerKey(@Nullable Integer configurationCacheEntriesPerKey) {
            this.configurationCacheEntriesPerKey = configurationCacheEntriesPerKey;
        }

        public @Nullable String getConfigurationCacheHeapDumpDir() {
            return configurationCacheHeapDumpDir;
        }

        public void setConfigurationCacheHeapDumpDir(@Nullable String configurationCacheHeapDumpDir) {
            this.configurationCacheHeapDumpDir = configurationCacheHeapDumpDir;
        }

        public @Nullable Boolean getConfigurationCacheFineGrainedPropertyTracking() {
            return configurationCacheFineGrainedPropertyTracking;
        }

        public void setConfigurationCacheFineGrainedPropertyTracking(@Nullable Boolean configurationCacheFineGrainedPropertyTracking) {
            this.configurationCacheFineGrainedPropertyTracking = configurationCacheFineGrainedPropertyTracking;
        }

        public @Nullable Boolean getIsolatedProjects() {
            return isolatedProjects;
        }

        public void setIsolatedProjects(@Nullable Boolean isolatedProjects) {
            this.isolatedProjects = isolatedProjects;
        }

        public @Nullable Boolean getProblemReportGeneration() {
            return problemReportGeneration;
        }

        public void enableProblemReportGeneration(@Nullable Boolean problemReportGeneration) {
            this.problemReportGeneration = problemReportGeneration;
        }

        public @Nullable Boolean getPropertyUpgradeReportEnabled() {
            return propertyUpgradeReportEnabled;
        }

        public void setPropertyUpgradeReportEnabled(@Nullable Boolean propertyUpgradeReportEnabled) {
            this.propertyUpgradeReportEnabled = propertyUpgradeReportEnabled;
        }

        public @Nullable Boolean getTaskGraph() {
            return taskGraph;
        }

        public void setTaskGraph(@Nullable Boolean taskGraph) {
            this.taskGraph = taskGraph;
        }

        public @Nullable Boolean getParallelToolingModelBuilding() {
            return parallelToolingModelBuilding;
        }

        public void setParallelToolingModelBuilding(@Nullable Boolean parallelToolingModelBuilding) {
            this.parallelToolingModelBuilding = parallelToolingModelBuilding;
        }
    }
}
