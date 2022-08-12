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
import org.gradle.internal.buildoption.BuildOptionSet;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.Origin;
import org.gradle.internal.buildoption.StringBuildOption;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuildLayoutParametersBuildOptions extends BuildOptionSet<BuildLayoutParameters> {

    private static List<BuildOption<? super BuildLayoutParameters>> options;

    static {
        List<BuildOption<BuildLayoutParameters>> options = new ArrayList<BuildOption<BuildLayoutParameters>>();
        options.add(new GradleUserHomeOption());
        options.add(new ProjectDirOption());
        BuildLayoutParametersBuildOptions.options = Collections.unmodifiableList(options);
    }

    @Override
    public List<BuildOption<? super BuildLayoutParameters>> getAllOptions() {
        return options;
    }

    public static class GradleUserHomeOption extends StringBuildOption<BuildLayoutParameters> {
        public GradleUserHomeOption() {
            super(BuildLayoutParameters.GRADLE_USER_HOME_PROPERTY_KEY, CommandLineOptionConfiguration.create("gradle-user-home", "g", "Specifies the Gradle user home directory. Defaults to ~/.gradle"));
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
            File projectDir = resolver.transform(value);
            settings.setCurrentDir(projectDir);
            settings.setProjectDir(projectDir);
        }
    }
}
