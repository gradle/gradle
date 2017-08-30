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

import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.BasicFileResolver;
import org.gradle.initialization.option.BooleanBuildOption;
import org.gradle.initialization.option.BuildOption;
import org.gradle.initialization.option.BuildOptionFactory;
import org.gradle.initialization.option.CommandLineOptionConfiguration;
import org.gradle.initialization.option.StringBuildOption;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StartParameterBuildOptionFactory implements BuildOptionFactory<StartParameter> {

    @Override
    public List<BuildOption<StartParameter>> create() {
        List<BuildOption<StartParameter>> options = new ArrayList<BuildOption<StartParameter>>();
        options.add(new ProjectCacheDirOption());
        options.add(new ConfigureOnDemandOption());
        options.add(new BuildCacheOption());
        options.add(new BuildScanOption());
        return options;
    }

    public static class ProjectCacheDirOption extends StringBuildOption<StartParameter> {
        public ProjectCacheDirOption() {
            super(StartParameter.class, null, CommandLineOptionConfiguration.create("project-cache-dir", "Specify the project-specific cache directory. Defaults to .gradle in the root project directory.").hasArgument());
        }

        @Override
        public void applyTo(String value, StartParameter settings) {
            Transformer<File, String> resolver = new BasicFileResolver(settings.getCurrentDir());
            settings.setProjectCacheDir(resolver.transform(value));
        }
    }

    public static class ConfigureOnDemandOption extends BooleanBuildOption<StartParameter> {
        public static final String GRADLE_PROPERTY = "org.gradle.configureondemand";

        public ConfigureOnDemandOption() {
            super(StartParameter.class, GRADLE_PROPERTY, CommandLineOptionConfiguration.create("configure-on-demand", "Configure necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.").incubating());
        }

        @Override
        public void applyTo(boolean value, StartParameter settings) {
            settings.setConfigureOnDemand(value);
        }
    }

    public static class BuildCacheOption extends BooleanBuildOption<StartParameter> {
        public static final String GRADLE_PROPERTY = "org.gradle.caching";

        public BuildCacheOption() {
            super(StartParameter.class, GRADLE_PROPERTY, CommandLineOptionConfiguration.create("build-cache", "Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.").incubating());
        }

        @Override
        public void applyTo(boolean value, StartParameter settings) {
            settings.setBuildCacheEnabled(value);
        }
    }

    public static class BuildScanOption extends BooleanBuildOption<StartParameter> {
        public BuildScanOption() {
            super(StartParameter.class, null, CommandLineOptionConfiguration.create("scan", "Creates a build scan. Gradle will emit a warning if the build scan plugin has not been applied. (https://gradle.com/build-scans)").incubating());
        }

        @Override
        public void applyTo(boolean value, StartParameter settings) {
            settings.setBuildScan(value);
        }
    }
}
