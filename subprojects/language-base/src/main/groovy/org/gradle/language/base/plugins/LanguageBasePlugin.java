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
package org.gradle.language.base.plugins;

import org.gradle.api.*;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.BinaryContainer;
import org.gradle.language.base.internal.BinaryInternal;
import org.gradle.language.base.internal.DefaultBinaryContainer;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.model.ModelRules;

import javax.inject.Inject;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.language.base.BinaryContainer} named {@code binaries} to the project.
 * Adds a {@link org.gradle.language.base.ProjectSourceSet} named {@code sources} to the project.
 */
@Incubating
public class LanguageBasePlugin implements Plugin<Project> {
    public static final String BUILD_GROUP = "build";

    private final Instantiator instantiator;
    private final ModelRules modelRules;

    @Inject
    public LanguageBasePlugin(Instantiator instantiator, ModelRules modelRules) {
        this.instantiator = instantiator;
        this.modelRules = modelRules;
    }

    public void apply(final Project target) {
        target.getExtensions().create("sources", DefaultProjectSourceSet.class, instantiator);
        final BinaryContainer binaries = target.getExtensions().create("binaries", DefaultBinaryContainer.class, instantiator);

        modelRules.register("binaries", BinaryContainer.class, new Factory<BinaryContainer>() {
            public BinaryContainer create() {
                return binaries;
            }
        });

        binaries.withType(BinaryInternal.class).all(new Action<BinaryInternal>() {
            public void execute(BinaryInternal binary) {
                Task binaryLifecycleTask = target.task(binary.getNamingScheme().getLifecycleTaskName());
                binaryLifecycleTask.setGroup(BUILD_GROUP);
                binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                binary.setLifecycleTask(binaryLifecycleTask);
            }
        });
    }
}
