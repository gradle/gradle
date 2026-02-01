/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.buildinit.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.Provider;
import org.gradle.buildinit.InsecureProtocolOption;
import org.gradle.buildinit.tasks.InitBuild;

/**
 * The build init plugin.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/build_init_plugin.html">Build Init plugin reference</a>
 */
public abstract class BuildInitPlugin implements Plugin<Project> {

    private static final String COMMENTS_PROPERTY = "org.gradle.buildinit.comments";

    @Override
    public void apply(Project project) {
        if (project.getParent() == null) {
            project.getTasks().register("init", InitBuild.class, initBuild -> {
                initBuild.setGroup("Build Setup");
                initBuild.setDescription("Initializes a new Gradle build.");

                ProjectInternal projectInternal = (ProjectInternal) project;

                ProjectInternal.DetachedResolver detachedResolver = projectInternal.newDetachedResolver();
                initBuild.getProjectLayoutRegistry().getBuildConverter().configureClasspath(
                    detachedResolver, project.getObjects(), projectInternal.getServices().get(JvmPluginServices.class));

                initBuild.getUseDefaults().convention(false);
                initBuild.getInsecureProtocol().convention(InsecureProtocolOption.WARN);
                initBuild.getAllowFileOverwrite().convention(false);
                initBuild.getComments().convention(getCommentsProperty(project).orElse(true));
                initBuild.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            });
        }
    }

    private static Provider<Boolean> getCommentsProperty(Project project) {
        return project.getProviders().gradleProperty(COMMENTS_PROPERTY).map(SerializableLambdas.transformer(Boolean::parseBoolean));
    }
}
