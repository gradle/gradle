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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.BasicFileResolver;
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
import org.gradle.internal.watch.registry.WatchMode;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StartParameterBuildOptions extends BuildOptionSet<StartParameterInternal> {

    private static List<BuildOption<StartParameterInternal>> options;

    static {
        List<BuildOption<StartParameterInternal>> options = new ArrayList<>();
        options.add(new ProjectCacheDirOption());
        options.add(new RerunTasksOption());
        options.add(new ProfileOption());
        options.add(new ContinueOption());
        options.add(new OfflineOption());
        options.add(new RefreshDependenciesOption());
        options.add(new DryRunOption());
        options.add(new ContinuousOption());
        options.add(new ContinuousBuildQuietPeriodOption());
        options.add(new NoProjectDependenciesRebuildOption());
        options.add(new InitScriptOption());
        options.add(new ExcludeTaskOption());
        options.add(new IncludeBuildOption());
        options.add(new ConfigureOnDemandOption());
        options.add(new BuildCacheOption());
        options.add(new BuildCacheDebugLoggingOption());
        options.add(new WatchFileSystemOption());
        options.add(new WatchFileSystemDebugLoggingOption());
        options.add(new VfsVerboseLoggingOption());
        options.add(new BuildScanOption());
        options.add(new DependencyLockingWriteOption());
        options.add(new DependencyVerificationWriteOption());
        options.add(new DependencyVerificationModeOption());
        options.add(new DependencyLockingUpdateOption());
        options.add(new RefreshKeysOption());
        options.add(new ExportKeysOption());
        options.add(new ConfigurationCacheProblemsOption());
        options.add(new ConfigurationCacheOption());
        options.add(new IsolatedProjectsOption());
        options.add(new ConfigurationCacheMaxProblemsOption());
        options.add(new ConfigurationCacheDebugOption());
        options.add(new ConfigurationCacheRecreateOption());
        options.add(new ConfigurationCacheQuietOption());
        StartParameterBuildOptions.options = Collections.unmodifiableList(options);
    }

    @Override
    public List<? extends BuildOption<? super StartParameterInternal>> getAllOptions() {
        return options;
    }

    public static class ProjectCacheDirOption extends StringBuildOption<StartParameterInternal> {
        public ProjectCacheDirOption() {
            super(null, CommandLineOptionConfiguration.create("project-cache-dir", "Specify the project-specific cache directory. Defaults to .gradle in the root project directory."));
        }

        @Override
        public void applyTo(String value, StartParameterInternal settings, Origin origin) {
            Transformer<File, String> resolver = new BasicFileResolver(settings.getCurrentDir());
            settings.setProjectCacheDir(resolver.transform(value));
        }
    }

    public static class RerunTasksOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        public RerunTasksOption() {
            super(null, CommandLineOptionConfiguration.create("rerun-tasks", "Ignore previously cached task results."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setRerunTasks(true);
        }
    }

    public static class ProfileOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        public ProfileOption() {
            super(null, CommandLineOptionConfiguration.create("profile", "Profile build execution time and generates a report in the <build_dir>/reports/profile directory."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setProfile(true);
        }
    }

    public static class ContinueOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        public static final String LONG_OPTION = "continue";

        public ContinueOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, "Continue task execution after a task failure."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setContinueOnFailure(true);
        }
    }

    public static class OfflineOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        public OfflineOption() {
            super(null, CommandLineOptionConfiguration.create("offline", "Execute the build without accessing network resources."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setOffline(true);
        }
    }

    public static class RefreshDependenciesOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        public RefreshDependenciesOption() {
            super(null, CommandLineOptionConfiguration.create("refresh-dependencies", "U", "Refresh the state of dependencies."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setRefreshDependencies(true);
        }
    }

    public static class DryRunOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        public DryRunOption() {
            super(null, CommandLineOptionConfiguration.create("dry-run", "m", "Run the builds with all task actions disabled."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setDryRun(true);
        }
    }

    public static class ContinuousOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        public ContinuousOption() {
            super(null, CommandLineOptionConfiguration.create("continuous", "t", "Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setContinuous(true);
        }
    }

    public static class ContinuousBuildQuietPeriodOption extends IntegerBuildOption<StartParameterInternal> {

        public static final String PROPERTY_NAME = "org.gradle.continuous.quietperiod";

        public ContinuousBuildQuietPeriodOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(int quietPeriodMillis, StartParameterInternal startParameter, Origin origin) {
            startParameter.setContinuousBuildQuietPeriod(Duration.ofMillis(quietPeriodMillis));
        }
    }

    public static class NoProjectDependenciesRebuildOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        private static final String LONG_OPTION = "no-rebuild";
        private static final String SHORT_OPTION = "a";

        public NoProjectDependenciesRebuildOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, "Do not rebuild project dependencies."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setBuildProjectDependencies(false);
        }
    }

    public static class InitScriptOption extends ListBuildOption<StartParameterInternal> {
        public InitScriptOption() {
            super(null, CommandLineOptionConfiguration.create("init-script", "I", "Specify an initialization script."));
        }

        @Override
        public void applyTo(List<String> values, StartParameterInternal settings, Origin origin) {
            Transformer<File, String> resolver = new BasicFileResolver(settings.getCurrentDir());

            for (String script : values) {
                settings.addInitScript(resolver.transform(script));
            }
        }
    }

    public static class ExcludeTaskOption extends ListBuildOption<StartParameterInternal> {
        public ExcludeTaskOption() {
            super(null, CommandLineOptionConfiguration.create("exclude-task", "x", "Specify a task to be excluded from execution."));
        }

        @Override
        public void applyTo(List<String> values, StartParameterInternal settings, Origin origin) {
            settings.setExcludedTaskNames(values);
        }
    }

    public static class IncludeBuildOption extends ListBuildOption<StartParameterInternal> {
        public IncludeBuildOption() {
            super(null, CommandLineOptionConfiguration.create("include-build", "Include the specified build in the composite."));
        }

        @Override
        public void applyTo(List<String> values, StartParameterInternal settings, Origin origin) {
            Transformer<File, String> resolver = new BasicFileResolver(settings.getCurrentDir());

            for (String includedBuild : values) {
                settings.includeBuild(resolver.transform(includedBuild));
            }
        }
    }

    public static class ConfigureOnDemandOption extends BooleanBuildOption<StartParameterInternal> {
        public static final String GRADLE_PROPERTY = "org.gradle.configureondemand";

        public ConfigureOnDemandOption() {
            super(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create("configure-on-demand", "Configure necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.", "Disables the use of configuration on demand.").incubating());
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            settings.setConfigureOnDemand(value);
        }
    }

    public static class BuildCacheOption extends BooleanBuildOption<StartParameterInternal> {
        public static final String GRADLE_PROPERTY = "org.gradle.caching";

        public BuildCacheOption() {
            super(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create("build-cache", "Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.", "Disables the Gradle build cache."));
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            settings.setBuildCacheEnabled(value);
        }
    }

    public static class BuildCacheDebugLoggingOption extends BooleanBuildOption<StartParameterInternal> {
        public static final String GRADLE_PROPERTY = "org.gradle.caching.debug";

        public BuildCacheDebugLoggingOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            settings.setBuildCacheDebugLogging(value);
        }
    }

    public static class WatchFileSystemOption extends BooleanBuildOption<StartParameterInternal> {
        public static final String LONG_OPTION = "watch-fs";
        public static final String GRADLE_PROPERTY = "org.gradle.vfs.watch";

        public WatchFileSystemOption() {
            super(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create(
                LONG_OPTION,
                "Enables watching the file system for changes, allowing data about the file system to be re-used for the next build.",
                "Disables watching the file system."
            ));
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal startParameter, Origin origin) {
            startParameter.setWatchFileSystemMode(value
                ? WatchMode.ENABLED
                : WatchMode.DISABLED
            );
        }
    }

    public static class WatchFileSystemDebugLoggingOption extends BooleanBuildOption<StartParameterInternal> {
        public static final String GRADLE_PROPERTY = "org.gradle.vfs.watch.debug";

        public WatchFileSystemDebugLoggingOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal startParameter, Origin origin) {
            startParameter.setWatchFileSystemDebugLogging(value);
        }
    }

    public static class VfsVerboseLoggingOption extends BooleanBuildOption<StartParameterInternal> {
        public static final String GRADLE_PROPERTY = "org.gradle.vfs.verbose";

        public VfsVerboseLoggingOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal startParameter, Origin origin) {
            startParameter.setVfsVerboseLogging(value);
        }
    }

    public static class BuildScanOption extends BooleanBuildOption<StartParameterInternal> {
        public static final String LONG_OPTION = "scan";

        public BuildScanOption() {
            super(null, BooleanCommandLineOptionConfiguration.create(LONG_OPTION, "Creates a build scan. Gradle will emit a warning if the build scan plugin has not been applied. (https://gradle.com/build-scans)", "Disables the creation of a build scan. For more information about build scans, please visit https://gradle.com/build-scans."));
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            if (value) {
                settings.setBuildScan(true);
            } else {
                settings.setNoBuildScan(true);
            }
        }
    }

    public static class DependencyLockingWriteOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {
        public static final String LONG_OPTION = "write-locks";

        public DependencyLockingWriteOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, "Persists dependency resolution for locked configurations, ignoring existing locking information if it exists"));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setWriteDependencyLocks(true);
        }
    }

    public static class DependencyVerificationWriteOption extends StringBuildOption<StartParameterInternal> {
        public static final String SHORT_OPTION = "M";
        public static final String LONG_OPTION = "write-verification-metadata";

        DependencyVerificationWriteOption() {
            super(null, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION,
                "Generates checksums for dependencies used in the project (comma-separated list)"));
        }

        @Override
        public void applyTo(String value, StartParameterInternal settings, Origin origin) {
            List<String> checksums = Splitter.on(",")
                .omitEmptyStrings()
                .trimResults()
                .splitToList(value)
                .stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
            settings.setWriteDependencyVerifications(checksums);
        }
    }

    public static class DependencyVerificationModeOption extends EnumBuildOption<DependencyVerificationMode, StartParameterInternal> {

        private static final String GRADLE_PROPERTY = "org.gradle.dependency.verification";
        private static final String LONG_OPTION = "dependency-verification";
        private static final String SHORT_OPTION = "F";

        public DependencyVerificationModeOption() {
            super(LONG_OPTION,
                DependencyVerificationMode.class,
                DependencyVerificationMode.values(),
                GRADLE_PROPERTY,
                CommandLineOptionConfiguration.create(
                    LONG_OPTION, SHORT_OPTION, "Configures the dependency verification mode. Values are 'strict', 'lenient' or 'off'.")
            );
        }

        @Override
        public void applyTo(DependencyVerificationMode value, StartParameterInternal settings, Origin origin) {
            settings.setDependencyVerificationMode(value);
        }
    }

    public static class DependencyLockingUpdateOption extends ListBuildOption<StartParameterInternal> {

        public DependencyLockingUpdateOption() {
            super(null, CommandLineOptionConfiguration.create("update-locks", "Perform a partial update of the dependency lock, letting passed in module notations change version.").incubating());
        }

        @Override
        public void applyTo(List<String> modulesToUpdate, StartParameterInternal settings, Origin origin) {
            settings.setLockedDependenciesToUpdate(modulesToUpdate);
        }
    }

    public static class RefreshKeysOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {

        private static final String LONG_OPTION = "refresh-keys";

        public RefreshKeysOption() {
            super(null,
                CommandLineOptionConfiguration.create(LONG_OPTION, "Refresh the public keys used for dependency verification."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setRefreshKeys(true);
        }
    }

    public static class ExportKeysOption extends EnabledOnlyBooleanBuildOption<StartParameterInternal> {

        private static final String LONG_OPTION = "export-keys";

        public ExportKeysOption() {
            super(null,
                CommandLineOptionConfiguration.create(LONG_OPTION, "Exports the public keys used for dependency verification."));
        }

        @Override
        public void applyTo(StartParameterInternal settings, Origin origin) {
            settings.setExportKeys(true);
        }
    }

    public static class ConfigurationCacheOption extends BooleanBuildOption<StartParameterInternal> {

        public static final String PROPERTY_NAME = "org.gradle.unsafe.configuration-cache";
        public static final String LONG_OPTION = "configuration-cache";

        public ConfigurationCacheOption() {
            super(
                PROPERTY_NAME,
                BooleanCommandLineOptionConfiguration.create(
                    LONG_OPTION,
                    "Enables the configuration cache. Gradle will try to reuse the build configuration from previous builds.",
                    "Disables the configuration cache."
                ).incubating()
            );
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            settings.setConfigurationCache(Value.value(value));
        }
    }

    public static class IsolatedProjectsOption extends BooleanBuildOption<StartParameterInternal> {
        public static final String PROPERTY_NAME = "org.gradle.unsafe.isolated-projects";

        public IsolatedProjectsOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            settings.setIsolatedProjects(Value.value(value));
        }
    }

    public static class ConfigurationCacheProblemsOption extends EnumBuildOption<ConfigurationCacheProblemsOption.Value, StartParameterInternal> {
        public static final String PROPERTY_NAME = "org.gradle.unsafe.configuration-cache-problems";
        public static final String LONG_OPTION = "configuration-cache-problems";

        public enum Value {
            FAIL, WARN
        }

        public ConfigurationCacheProblemsOption() {
            super(
                LONG_OPTION,
                Value.class,
                Value.values(),
                PROPERTY_NAME,
                CommandLineOptionConfiguration.create(
                    LONG_OPTION,
                    "Configures how the configuration cache handles problems (fail or warn). Defaults to fail."
                ).incubating()
            );
        }

        @Override
        public void applyTo(Value value, StartParameterInternal settings, Origin origin) {
            settings.setConfigurationCacheProblems(value);
        }
    }

    public static class ConfigurationCacheMaxProblemsOption extends IntegerBuildOption<StartParameterInternal> {

        public static final String PROPERTY_NAME = "org.gradle.unsafe.configuration-cache.max-problems";

        public ConfigurationCacheMaxProblemsOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(int value, StartParameterInternal settings, Origin origin) {
            settings.setConfigurationCacheMaxProblems(value);
        }
    }

    public static class ConfigurationCacheDebugOption extends BooleanBuildOption<StartParameterInternal> {

        public static final String PROPERTY_NAME = "org.gradle.unsafe.configuration-cache.debug";

        public ConfigurationCacheDebugOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            settings.setConfigurationCacheDebug(value);
        }
    }

    public static class ConfigurationCacheRecreateOption extends BooleanBuildOption<StartParameterInternal> {

        public static final String PROPERTY_NAME = "org.gradle.unsafe.configuration-cache.recreate-cache";

        public ConfigurationCacheRecreateOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            settings.setConfigurationCacheRecreateCache(value);
        }
    }

    public static class ConfigurationCacheQuietOption extends BooleanBuildOption<StartParameterInternal> {

        public static final String PROPERTY_NAME = "org.gradle.unsafe.configuration-cache.quiet";

        public ConfigurationCacheQuietOption() {
            super(PROPERTY_NAME);
        }

        @Override
        public void applyTo(boolean value, StartParameterInternal settings, Origin origin) {
            settings.setConfigurationCacheQuiet(value);
        }
    }
}
