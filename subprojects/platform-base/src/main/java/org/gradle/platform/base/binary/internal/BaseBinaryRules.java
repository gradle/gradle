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

package org.gradle.platform.base.binary.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.Finalize;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.internal.BinarySpecInternal;

/**
 * Base binary spec rules.
 */
public class BaseBinaryRules extends RuleSource {
    @Finalize
    public void defineBuildLifecycleTask(BinarySpecInternal binary, ITaskFactory taskFactory) {
        if (!binary.isLegacyBinary()) {
            TaskInternal binaryLifecycleTask = taskFactory.create(binary.getProjectScopedName(), DefaultTask.class);
            binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
            binary.setBuildTask(binaryLifecycleTask);
        }
    }
}
