/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.initialization.internal.SharedModelDefaultsInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.features.internal.binding.ProjectFeatureImplementation;
import org.gradle.features.internal.binding.ProjectFeatureDeclarations;
import org.gradle.util.internal.ClosureBackedAction;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultSharedModelDefaults implements SharedModelDefaultsInternal, MethodMixIn {
    private final ProjectFeatureDeclarations projectFeatureDeclarations;
    private final DynamicMethods dynamicMethods = new DynamicMethods();
    private final List<ModelDefaultRegistration<?>> registrations = new ArrayList<>();
    boolean processed = false;

    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<ProjectLayout> projectLayout = new ThreadLocal<>();

    @Inject
    public DefaultSharedModelDefaults(ProjectFeatureDeclarations projectFeatureDeclarations) {
        this.projectFeatureDeclarations = projectFeatureDeclarations;
    }

    @Override
    public void setProjectLayout(ProjectLayout projectLayout) {
        this.projectLayout.set(projectLayout);
    }

    @Override
    public void clearProjectLayout() {
        projectLayout.remove();
    }

    @Override
    public ProjectLayout getLayout() {
        ProjectLayout instance = projectLayout.get();
        if (instance == null) {
            throw new GradleException("ProjectLayout should be referenced only inside of project type default configuration blocks");
        }
        return instance;
    }

    @Override
    public void processRegistrations() {
        if (processed) {
            return;
        }

        registrations.forEach(registration -> {
            if (projectFeatureDeclarations.getProjectFeatureImplementations().containsKey(registration.name)) {
                Set<ProjectFeatureImplementation<?, ?>> implementations = projectFeatureDeclarations.getProjectFeatureImplementations().get(registration.name);
                if (implementations.isEmpty()) {
                    throw new IllegalArgumentException(String.format("Cannot add default for project type '%s' because it has no implementations.", registration.name));
                }
                // TODO - this works for now because we only have one implementation per project type, but we need to revisit this when we support defaults
                // for features where we could have multiple implementations binding to different target types
                if (implementations.size() > 1) {
                    throw new IllegalArgumentException(String.format("Cannot add default for project feature '%s' because it has multiple registered implementations.", registration.name));
                }
                ProjectFeatureImplementation<?, ?> projectFeature = implementations.iterator().next();
                if (projectFeature.getDefinitionPublicType().isAssignableFrom(registration.publicType)) {
                    projectFeature.addModelDefault(new ActionBasedDefault<>(registration.action));
                } else {
                    throw new IllegalArgumentException(String.format("Cannot add default for project type '%s' with public type '%s'. Expected public type to be assignable from '%s'.", registration.name, registration.publicType, projectFeature.getDefinitionPublicType()));
                }
            } else {
                throw new IllegalArgumentException(String.format("Cannot add default for unknown project type '%s'.", registration.name));
            }
        });

        processed = true;
    }

    @Override
    public <T> void add(String name, Class<T> publicType, Action<? super T> configureAction) {
        if (processed) {
            throw new IllegalStateException("Cannot add shared model defaults after processing.");
        }
        registrations.add(new ModelDefaultRegistration<>(name, publicType, configureAction));
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return dynamicMethods;
    }

    class DynamicMethods implements MethodAccess {
        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return arguments.length == 1 &&
                (arguments[0] instanceof Action || arguments[0] instanceof Closure) &&
                projectFeatureDeclarations.getProjectFeatureImplementations().containsKey(name);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            if (hasMethod(name, arguments)) {
                Set<ProjectFeatureImplementation<?, ?>> implementations = projectFeatureDeclarations.getProjectFeatureImplementations().get(name);
                if (implementations.isEmpty()) {
                    throw new IllegalArgumentException(String.format("Cannot resolve default for project type '%s' because it has no implementations.", name));
                }

                if (implementations.size() > 1) {
                    throw new IllegalArgumentException(String.format("Cannot resolve default for project feature '%s' because it has multiple registered implementations.", name));
                }
                ProjectFeatureImplementation<?, ?> implementation = implementations.iterator().next();
                add(name, implementation.getDefinitionPublicType(), Cast.uncheckedNonnullCast(toAction(arguments[0])));
                return DynamicInvokeResult.found();
            }

            return DynamicInvokeResult.notFound();
        }

        private Action<?> toAction(Object argument) {
            if (argument instanceof Action) {
                return Cast.uncheckedNonnullCast(argument);
            } else if (argument instanceof Closure) {
                return ClosureBackedAction.of((Closure<?>) argument);
            } else {
                throw new IllegalArgumentException("Expected an Action or Closure, but received: " + argument);
            }
        }
    }

    static class ModelDefaultRegistration<T> {
        final String name;
        final Class<T> publicType;
        final Action<? super T> action;

        public ModelDefaultRegistration(String name, Class<T> publicType, Action<? super T> action) {
            this.name = name;
            this.publicType = publicType;
            this.action = action;
        }
    }
}
