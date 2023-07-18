/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.internal.FinalizableValue;
import org.gradle.jvm.toolchain.JavaToolchainRepositoryHandler;
import org.gradle.jvm.toolchain.JvmToolchainManagement;

import javax.inject.Inject;

public abstract class DefaultJvmToolchainManagement implements JvmToolchainManagement, FinalizableValue {

    private final JavaToolchainResolverRegistryInternal registry;

    @Inject
    public DefaultJvmToolchainManagement(JavaToolchainResolverRegistryInternal registry) {
        this.registry = registry;
    }

    @Override
    public JavaToolchainRepositoryHandler getJavaRepositories() {
        return registry.getRepositories();
    }

    @Override
    public void javaRepositories(Action<? super JavaToolchainRepositoryHandler> configureAction) {
        configureAction.execute(getJavaRepositories());
    }

    @Override
    public void preventFromFurtherMutation() {
        registry.preventFromFurtherMutation();
    }
}
