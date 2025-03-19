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
import org.gradle.plugin.software.internal.SoftwareFeatureImplementation;
import org.gradle.plugin.software.internal.SoftwareFeatureRegistry;
import org.gradle.util.internal.ClosureBackedAction;

import javax.inject.Inject;

public class DefaultSharedModelDefaults implements SharedModelDefaultsInternal, MethodMixIn {
    private final SoftwareFeatureRegistry softwareFeatureRegistry;
    private final DynamicMethods dynamicMethods = new DynamicMethods();

    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<ProjectLayout> projectLayout = new ThreadLocal<>();

    @Inject
    public DefaultSharedModelDefaults(SoftwareFeatureRegistry softwareFeatureRegistry) {
        this.softwareFeatureRegistry = softwareFeatureRegistry;
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
            throw new GradleException("ProjectLayout should be referenced only inside of software type default configuration blocks");
        }
        return instance;
    }

    @Override
    public <T> void add(String name, Class<T> publicType, Action<? super T> configureAction) {
        if (softwareFeatureRegistry.getSoftwareFeatureImplementations().containsKey(name)) {
            SoftwareFeatureImplementation<?> softwareFeature = softwareFeatureRegistry.getSoftwareFeatureImplementations().get(name);
            if (softwareFeature.getModelPublicType().isAssignableFrom(publicType)) {
                softwareFeature.addModelDefault(new ActionBasedDefault<>(configureAction));
            } else {
                throw new IllegalArgumentException(String.format("Cannot add convention for software type '%s' with public type '%s'. Expected public type to be assignable from '%s'.", name, publicType, softwareFeature.getModelPublicType()));
            }
        } else {
            throw new IllegalArgumentException(String.format("Cannot add convention for unknown software type '%s'.", name));
        }
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
                softwareFeatureRegistry.getSoftwareFeatureImplementations().containsKey(name);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            if (hasMethod(name, arguments)) {
                SoftwareFeatureImplementation<?> softwareFeature = softwareFeatureRegistry.getSoftwareFeatureImplementations().get(name);
                add(name, softwareFeature.getModelPublicType(), Cast.uncheckedNonnullCast(toAction(arguments[0])));
                return DynamicInvokeResult.found();
            } else {
                return DynamicInvokeResult.notFound();
            }
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
}
