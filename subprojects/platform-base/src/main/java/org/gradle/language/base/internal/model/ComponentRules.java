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

import com.google.common.base.Joiner;
import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetFactory;
import org.gradle.language.base.internal.registry.LanguageRegistration;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.model.*;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import java.io.File;

import static com.google.common.base.Strings.emptyToNull;

/**
 * Cross-cutting rules for all {@link org.gradle.platform.base.ComponentSpec} instances.
 */
@SuppressWarnings("UnusedDeclaration")
public class ComponentRules extends RuleSource {
    @Defaults
    void initializeSourceSets(ComponentSpecInternal component, LanguageSourceSetFactory languageSourceSetFactory, LanguageTransformContainer languageTransforms) {
        for (LanguageRegistration<?> languageRegistration : languageSourceSetFactory.getRegistrations()) {
            registerLanguageTypes(component, languageRegistration, languageTransforms);
        }
    }

    @Defaults
    void applyDefaultSourceConventions(ComponentSpec component, ProjectIdentifier projectIdentifier) {
        component.getSources().afterEach(new AddDefaultSourceLocation(projectIdentifier.getProjectDir()));
    }

    @Defaults
    void addSourcesSetsToProjectSourceSet(ComponentSpec component, final ProjectSourceSet projectSourceSet) {
        component.getSources().afterEach(new Action<LanguageSourceSet>() {
            @Override
            public void execute(LanguageSourceSet languageSourceSet) {
                projectSourceSet.add(languageSourceSet);
            }
        });
    }

    // If there is a transform for the language into one of the component inputs, add a default source set
    private <U extends LanguageSourceSet> void registerLanguageTypes(ComponentSpecInternal component, LanguageRegistration<U> languageRegistration, LanguageTransformContainer languageTransforms) {
        for (LanguageTransform<?, ?> languageTransform : languageTransforms) {
            if (ModelType.of(languageTransform.getSourceSetType()).equals(languageRegistration.getSourceSetType())
                && component.getInputTypes().contains(languageTransform.getOutputType())) {
                component.getSources().create(languageRegistration.getName(), languageRegistration.getSourceSetType().getConcreteClass());
                return;
            }
        }
    }

    @Rules
    void inputRules(AttachInputs attachInputs, ComponentSpec component) {
        attachInputs.setBinaries(component.getBinaries());
        attachInputs.setSources(component.getSources());
    }

    static abstract class AttachInputs extends RuleSource {
        @RuleTarget
        abstract ModelMap<BinarySpec> getBinaries();
        abstract void setBinaries(ModelMap<BinarySpec> binaries);

        @RuleInput
        abstract ModelMap<LanguageSourceSet> getSources();
        abstract void setSources(ModelMap<LanguageSourceSet> sources);

        @Mutate
        void initializeBinarySourceSets(ModelMap<BinarySpec> binaries) {
            // TODO - sources is not actual an input to binaries, it's an input to each binary
            binaries.withType(BinarySpecInternal.class).beforeEach(new Action<BinarySpecInternal>() {
                @Override
                public void execute(BinarySpecInternal binary) {
                    binary.getInputs().addAll(getSources().values());
                }
            });
        }
    }

    private static class AddDefaultSourceLocation implements Action<LanguageSourceSet> {
        private File baseDir;

        public AddDefaultSourceLocation(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void execute(LanguageSourceSet languageSourceSet) {
            // Only apply default locations when none explicitly configured
            if (languageSourceSet.getSource().getSrcDirs().isEmpty()) {
                String defaultSourceDir = calculateDefaultPath(languageSourceSet);
                languageSourceSet.getSource().srcDir(defaultSourceDir);
            }
        }

        private String calculateDefaultPath(LanguageSourceSet languageSourceSet) {
            return Joiner.on(File.separator).skipNulls().join(baseDir.getPath(), "src", emptyToNull(languageSourceSet.getParentName()), emptyToNull(languageSourceSet.getName()));
        }
    }
}
