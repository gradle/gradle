/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base.internal.model;

import com.google.common.collect.Lists;
import com.google.common.primitives.Booleans;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.JointCompileTaskConfig;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Creates source 'transformation' tasks based on the available {@link LanguageTransform}s.
 *
 * This class is a basic and somewhat hacky placeholder for true dependency-aware source handling:
 * - Source sets should be able to depend on other source sets, resulting in the correct task dependencies and inputs
 * - Joint-compilation should only be used in the case where sources are co-dependent.
 *
 * Currently we use joint-compilation when:
 * - We have a language transform that supports joint-compilation
 * - Binary is flagged with {@link BinarySpecInternal#hasCodependentSources()}.
 */
public class BinarySourceTransformations {
    private final TaskContainer tasks;
    private final Iterable<LanguageTransform<?, ?>> prioritizedTransforms;
    private final ServiceRegistry serviceRegistry;

    public BinarySourceTransformations(TaskContainer tasks, LanguageTransformContainer transforms, ServiceRegistry serviceRegistry) {
        this.tasks = tasks;
        this.prioritizedTransforms = prioritize(transforms);
        this.serviceRegistry = serviceRegistry;
    }

    public void createTasksFor(BinarySpecInternal binary) {
        Set<LanguageSourceSetInternal> sourceSetsToCompile = getSourcesToCompile(binary);
        for (LanguageTransform<?, ?> languageTransform : prioritizedTransforms) {

            if (!languageTransform.applyToBinary(binary)) {
                continue;
            }

            LanguageSourceSetInternal sourceSetToCompile;
            while ((sourceSetToCompile = findSourceFor(languageTransform, sourceSetsToCompile)) != null) {
                sourceSetsToCompile.remove(sourceSetToCompile);

                final SourceTransformTaskConfig taskConfig = languageTransform.getTransformTask();
                String taskName = getTransformTaskName(languageTransform, taskConfig, binary, sourceSetToCompile);
                @SuppressWarnings("deprecation")
                Task task = tasks.create(taskName, taskConfig.getTaskType());
                taskConfig.configureTask(task, binary, sourceSetToCompile, serviceRegistry);

                task.dependsOn(sourceSetToCompile);
                binary.getTasks().add(task);

                if (binary.hasCodependentSources() && taskConfig instanceof JointCompileTaskConfig) {
                    JointCompileTaskConfig jointCompileTaskConfig = (JointCompileTaskConfig) taskConfig;

                    Iterator<LanguageSourceSetInternal> candidateSourceSets = sourceSetsToCompile.iterator();
                    while (candidateSourceSets.hasNext()) {
                        LanguageSourceSetInternal candidate = candidateSourceSets.next();
                        if (jointCompileTaskConfig.canTransform(candidate)) {
                            jointCompileTaskConfig.configureAdditionalTransform(task, candidate);
                            candidateSourceSets.remove();
                        }
                    }
                }
            }
        }
        // Should really fail here if sourcesToCompile is not empty: no transform for this source set in this binary
    }

    private Iterable<LanguageTransform<?, ?>> prioritize(LanguageTransformContainer languageTransforms) {
        List<LanguageTransform<?, ?>> prioritized = Lists.newArrayList(languageTransforms);
        Collections.sort(prioritized, new Comparator<LanguageTransform<?, ?>>() {
            @Override
            public int compare(LanguageTransform<?, ?> o1, LanguageTransform<?, ?> o2) {
                boolean joint1 = o1.getTransformTask() instanceof JointCompileTaskConfig;
                boolean joint2 = o2.getTransformTask() instanceof JointCompileTaskConfig;
                return Booleans.trueFirst().compare(joint1, joint2);
            }
        });
        return prioritized;
    }

    private Set<LanguageSourceSetInternal> getSourcesToCompile(BinarySpecInternal binary) {
        LinkedHashSet<LanguageSourceSetInternal> sourceSets = new LinkedHashSet<>();
        for (LanguageSourceSet languageSourceSet : binary.getInputs()) {
            LanguageSourceSetInternal languageSourceSetInternal = (LanguageSourceSetInternal) languageSourceSet;
            if (languageSourceSetInternal.getMayHaveSources()) {
                sourceSets.add(languageSourceSetInternal);
            }
        }
        return sourceSets;
    }

    private String getTransformTaskName(LanguageTransform<?, ?> transform, SourceTransformTaskConfig taskConfig, BinarySpecInternal binary, LanguageSourceSetInternal sourceSetToCompile) {
        if (binary.hasCodependentSources() && taskConfig instanceof JointCompileTaskConfig) {
            return taskConfig.getTaskPrefix() + capitalize(binary.getProjectScopedName()) + capitalize(transform.getClass().getSimpleName());
        }
        return taskConfig.getTaskPrefix() + capitalize(binary.getProjectScopedName()) + capitalize(sourceSetToCompile.getProjectScopedName());
    }

    private LanguageSourceSetInternal findSourceFor(LanguageTransform<?, ?> languageTransform, Set<LanguageSourceSetInternal> sourceSetsToCompile) {
        for (LanguageSourceSetInternal candidate : sourceSetsToCompile) {
            if (languageTransform.getSourceSetType().isInstance(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
