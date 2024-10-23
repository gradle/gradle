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
import org.gradle.api.initialization.SharedModelDefaults;
import org.gradle.internal.Cast;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.plugin.software.internal.SoftwareTypeImplementation;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
import org.gradle.util.internal.ClosureBackedAction;

import javax.inject.Inject;

public class DefaultSharedModelDefaults implements SharedModelDefaults, MethodMixIn {
    private final SoftwareTypeRegistry softwareTypeRegistry;
    private final DynamicMethods dynamicMethods = new DynamicMethods();

    @Inject
    public DefaultSharedModelDefaults(SoftwareTypeRegistry softwareTypeRegistry) {
        this.softwareTypeRegistry = softwareTypeRegistry;
    }

    @Override
    public <T> void add(String name, Class<T> publicType, Action<? super T> configureAction) {
        if (softwareTypeRegistry.getSoftwareTypeImplementations().containsKey(name)) {
            SoftwareTypeImplementation<?> softwareType = softwareTypeRegistry.getSoftwareTypeImplementations().get(name);
            if (softwareType.getModelPublicType().isAssignableFrom(publicType)) {
                softwareType.addModelDefault(new ActionBasedDefault<>(configureAction));
            } else {
                throw new IllegalArgumentException(String.format("Cannot add convention for software type '%s' with public type '%s'. Expected public type to be assignable from '%s'.", name, publicType, softwareType.getModelPublicType()));
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
                softwareTypeRegistry.getSoftwareTypeImplementations().containsKey(name);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            if (hasMethod(name, arguments)) {
                SoftwareTypeImplementation<?> softwareType = softwareTypeRegistry.getSoftwareTypeImplementations().get(name);
                add(name, softwareType.getModelPublicType(), Cast.uncheckedNonnullCast(toAction(arguments[0])));
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
