/*
 * Copyright 2021 the original author or authors.
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

package org.gradleinternal.buildinit.plugins.internal.maven;

import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.buildinit.InsecureProtocolOption;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.File;
import java.util.Set;

abstract public class Maven2GradleWorkAction implements WorkAction<Maven2GradleWorkAction.Maven2GradleWorkParameters> {

    public interface Maven2GradleWorkParameters extends WorkParameters {
        DirectoryProperty getWorkingDir();
        Property<BuildInitDsl> getDsl();
        Property<Boolean> getUseIncubatingAPIs();
        Property<Settings> getMavenSettings();
        Property<InsecureProtocolOption> getInsecureProtocolOption();
    }

    @Override
    public void execute() {
        Maven2GradleWorkParameters params = getParameters();
        File pom = params.getWorkingDir().file("pom.xml").get().getAsFile();
        try {
            Set<MavenProject> mavenProjects = new MavenProjectsCreator().create(params.getMavenSettings().get(), pom);
            new Maven2Gradle(mavenProjects, params.getWorkingDir().get(), params.getDsl().get(), params.getUseIncubatingAPIs().get(), params.getInsecureProtocolOption().get()).convert();
        } catch (Exception exception) {
            throw new MavenConversionException(String.format("Could not convert Maven POM %s to a Gradle build.", pom), exception);
        }
    }
}
