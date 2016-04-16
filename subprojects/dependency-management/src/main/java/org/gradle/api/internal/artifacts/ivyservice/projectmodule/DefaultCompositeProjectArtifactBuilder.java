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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import com.google.common.collect.*;
import org.gradle.StartParameter;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.util.List;

public class DefaultCompositeProjectArtifactBuilder implements CompositeProjectArtifactBuilder {
    private final CompositeProjectComponentRegistry registry;
    private final GradleLauncherFactory gradleLauncherFactory;
    private final StartParameter requestedStartParameter;
    private final ServiceRegistry serviceRegistry;
    private final Multimap<String, String> executedTasks = LinkedHashMultimap.create();

    public DefaultCompositeProjectArtifactBuilder(CompositeProjectComponentRegistry registry,
                                                  GradleLauncherFactory gradleLauncherFactory,
                                                  StartParameter requestedStartParameter,
                                                  ServiceRegistry serviceRegistry) {
        this.registry = registry;
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.requestedStartParameter = requestedStartParameter;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public boolean build(String projectPath, Iterable<String> taskNames) {
        List<String> tasksToExecute = Lists.newArrayList();
        for (String taskName : taskNames) {
            if (executedTasks.put(projectPath, taskName)) {
                tasksToExecute.add(taskName);
            }
        }
        if (tasksToExecute.isEmpty()) {
            return false;
        }

        File projectDirectory = registry.getProjectDirectory(projectPath);
        StartParameter param = requestedStartParameter.newBuild();
        param.setProjectDir(projectDirectory);
        param.setTaskNames(tasksToExecute);

        GradleLauncher launcher = gradleLauncherFactory.newInstance(param, serviceRegistry);
        try {
            launcher.run();
        } finally {
            launcher.stop();
        }
        return true;
    }
}
