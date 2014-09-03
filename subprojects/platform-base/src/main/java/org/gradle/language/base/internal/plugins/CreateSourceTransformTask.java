/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.base.internal.plugins;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

public class CreateSourceTransformTask {
    private final LanguageRegistration<?> language;

    public CreateSourceTransformTask(LanguageRegistration<?> languageRegistration) {
        this.language = languageRegistration;
    }

    public void createCompileTasksForBinary(final TaskContainer tasks, BinarySpec binarySpec) {
        final BinarySpecInternal binary = (BinarySpecInternal) binarySpec;
        if (binary.isLegacyBinary() || !language.applyToBinary(binary)) {
            return;
        }

        final SourceTransformTaskConfig taskConfig = language.getTransformTask();
        binary.getSource().withType(language.getSourceSetType(), new Action<LanguageSourceSet>() {
            public void execute(LanguageSourceSet languageSourceSet) {
                LanguageSourceSetInternal sourceSet = (LanguageSourceSetInternal) languageSourceSet;
                if (sourceSet.getMayHaveSources()) {
                    String taskName = binary.getNamingScheme().getTaskName(taskConfig.getTaskPrefix(), sourceSet.getFullName());
                    Task task = tasks.create(taskName, taskConfig.getTaskType());

                    taskConfig.configureTask(task, binary, sourceSet);

                    task.dependsOn(sourceSet);
                    binary.getTasks().add(task);
                }
            }
        });
    }

}
