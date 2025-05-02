/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.model;

import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.function.Supplier;


/**
 * Factory for {@link CalculatedValueContainer}.
 */
@ServiceScope(Scope.BuildSession.class)
public class CalculatedValueContainerFactory implements CalculatedValueFactory {
    private final ProjectLeaseRegistry projectLeaseRegistry;
    private final NodeExecutionContext globalContext;

    public CalculatedValueContainerFactory(ProjectLeaseRegistry projectLeaseRegistry, ServiceRegistry buildScopeServices) {
        this.projectLeaseRegistry = projectLeaseRegistry;
        this.globalContext = buildScopeServices::get;
    }

    /**
     * Create a calculated value that may have dependencies or that may need to access mutable model state.
     */
    public <T, S extends ValueCalculator<? extends T>> CalculatedValueContainer<T, S> create(DisplayName displayName, S supplier) {
        return new CalculatedValueContainer<>(displayName, supplier, projectLeaseRegistry, globalContext);
    }

    @Override
    public <T> CalculatedValueContainer<T, ValueCalculator<T>> create(DisplayName displayName, Supplier<? extends T> supplier) {
        return new CalculatedValueContainer<>(displayName, new SupplierBackedCalculator<>(supplier), projectLeaseRegistry, globalContext);
    }

    @Override
    public <T> CalculatedValueContainer<T, ValueCalculator<T>> create(DisplayName displayName, T value) {
        return new CalculatedValueContainer<>(displayName, value);
    }

    private static class SupplierBackedCalculator<T> implements ValueCalculator<T> {
        private final Supplier<T> supplier;

        public SupplierBackedCalculator(Supplier<? extends T> supplier) {
            this.supplier = Cast.uncheckedCast(supplier);
        }

        @Override
        public T calculateValue(NodeExecutionContext context) {
            return supplier.get();
        }
    }
}
