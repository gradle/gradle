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

package org.gradle.plugins.ide.internal.tooling;

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.internal.jvm.Jvm;
import org.gradle.tooling.internal.build.DefaultBuildEnvironment;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.util.List;

/**
 * Builds the GradleProject that contains the project hierarchy and task information
 */
public class BuildEnvironmentBuilder implements ToolingModelBuilder {

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.build.BuildEnvironment");
    }

    public Object buildAll(String modelName, Project target) {
        File gradleUserHomeDir = target.getGradle().getGradleUserHomeDir();
        String gradleVersion = target.getGradle().getGradleVersion();
        File javaHome = Jvm.current().getJavaHome();
        List<String> jvmArgs = Lists.newArrayList();
        return new DefaultBuildEnvironment(gradleUserHomeDir, gradleVersion, javaHome, jvmArgs);
    }
}
