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

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.BasicFileResolver;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.EnabledOnlyBooleanBuildOption;
import org.gradle.internal.buildoption.Origin;
import org.gradle.internal.buildoption.StringBuildOption;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuildLayoutParametersBuildOptions {

    private static List<BuildOption<BuildLayoutParameters>> options;

    static {
        List<BuildOption<BuildLayoutParameters>> options = new ArrayList<BuildOption<BuildLayoutParameters>>();
        options.add(new GradleUserHomeOption());
        options.add(new ProjectDirOption());
        options.add(new NoSearchUpwardsOption());
        BuildLayoutParametersBuildOptions.options = Collections.unmodifiableList(options);
    }

    public static List<BuildOption<BuildLayoutParameters>> get() {
        return options;
    }

    private BuildLayoutParametersBuildOptions() {
    }

    public static class GradleUserHomeOption extends StringBuildOption<BuildLayoutParameters> {
        public GradleUserHomeOption() {
            super(BuildLayoutParameters.GRADLE_USER_HOME_PROPERTY_KEY, CommandLineOptionConfiguration.create("gradle-user-home", "g", "Specifies the gradle user home directory."));
        }

        @Override
        public void applyTo(String value, BuildLayoutParameters settings, Origin origin) {
            Transformer<File, String> resolver = new BasicFileResolver(settings.getCurrentDir());
            settings.setGradleUserHomeDir(resolver.transform(value));
        }
    }

    public static class ProjectDirOption extends StringBuildOption<BuildLayoutParameters> {
        public ProjectDirOption() {
            super(null, CommandLineOptionConfiguration.create("project-dir", "p", "Specifies the start directory for Gradle. Defaults to current directory."));
        }

        @Override
        public void applyTo(String value, BuildLayoutParameters settings, Origin origin) {
            Transformer<File, String> resolver = new BasicFileResolver(settings.getCurrentDir());
            settings.setProjectDir(resolver.transform(value));
        }
    }

    public static class NoSearchUpwardsOption extends EnabledOnlyBooleanBuildOption<BuildLayoutParameters> {
        public NoSearchUpwardsOption() {
            super(null, CommandLineOptionConfiguration.create("no-search-upward", "u", "Don't search in parent folders for a settings file."));
        }

        @Override
        public void applyTo(BuildLayoutParameters settings, Origin origin) {
            settings.setSearchUpwards(false);
        }
    }
}
