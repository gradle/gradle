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
import org.gradle.language.base.internal.registry.LanguageRegistration;
import org.gradle.language.base.internal.registry.LanguageRegistry;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import java.io.File;

import static com.google.common.base.Strings.emptyToNull;

/**
 * Cross-cutting rules for all {@link org.gradle.platform.base.ComponentSpec} instances.
 */
@SuppressWarnings("UnusedDeclaration")
public class ComponentRules extends RuleSource {
    @Defaults
    void initializeSourceSets(ComponentSpecInternal component, LanguageRegistry languageRegistry, LanguageTransformContainer languageTransforms) {
        for (LanguageRegistration<?> languageRegistration : languageRegistry) {
            ComponentSourcesRegistrationAction.create(languageRegistration, languageTransforms).execute(component);
        }
    }

    @Defaults
    void applyDefaultSourceConventions(final ComponentSpec component, final ProjectIdentifier projectIdentifier) {
        component.getSources().afterEach(new AddDefaultSourceLocation(projectIdentifier.getProjectDir()));
    }

    @Defaults
    void addSourcesSetsToProjectSourceSet(final ComponentSpec component, final ProjectSourceSet projectSourceSet) {
        component.getSources().afterEach(new Action<LanguageSourceSet>() {
            @Override
            public void execute(LanguageSourceSet languageSourceSet) {
                projectSourceSet.add(languageSourceSet);
            }
        });
    }

    // Currently needs to be a separate action since can't have parameterized utility methods in a RuleSource
    private static class ComponentSourcesRegistrationAction<U extends LanguageSourceSet> implements Action<ComponentSpecInternal> {
        private final LanguageRegistration<U> languageRegistration;
        private final LanguageTransformContainer languageTransforms;

        private ComponentSourcesRegistrationAction(LanguageRegistration<U> registration, LanguageTransformContainer languageTransforms) {
            this.languageRegistration = registration;
            this.languageTransforms = languageTransforms;
        }

        public static <U extends LanguageSourceSet> ComponentSourcesRegistrationAction<U> create(LanguageRegistration<U> registration, LanguageTransformContainer languageTransforms) {
            return new ComponentSourcesRegistrationAction<U>(registration, languageTransforms);
        }

        public void execute(ComponentSpecInternal componentSpecInternal) {
            registerLanguageTypes(componentSpecInternal);
        }

        // If there is a transform for the language into one of the component inputs, add a default source set
        void registerLanguageTypes(final ComponentSpecInternal component) {
            for (LanguageTransform<?, ?> languageTransform : languageTransforms) {
                if (ModelType.of(languageTransform.getSourceSetType()).equals(languageRegistration.getSourceSetType())
                    && component.getInputTypes().contains(languageTransform.getOutputType())) {
                    component.getSources().create(languageRegistration.getName(), languageRegistration.getSourceSetType().getConcreteClass());
                    return;
                }
            }
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
