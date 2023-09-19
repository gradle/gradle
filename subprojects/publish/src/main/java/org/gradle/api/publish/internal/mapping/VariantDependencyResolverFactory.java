/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.internal.mapping;

import org.gradle.api.component.SoftwareComponentVariant;

import javax.annotation.Nullable;

/**
 * Creates {@link VariantDependencyResolver} instances for the given variant, taking into account
 * whether version mapping is enabled or not.
 */
public interface VariantDependencyResolverFactory {

    /**
     * Create a {@link VariantDependencyResolver} for the given variant, which is
     * backed by the publication's configured version mapping strategy.
     */
    VariantDependencyResolver createResolver(
        SoftwareComponentVariant variant,
        DeclaredVersionTransformer declaredVersionTransformer
    );

    /**
     * Transforms a dependency's declared version to the version that should be published,
     * if that dependency could not otherwise be resolved to a suitable version.
     */
    interface DeclaredVersionTransformer {
        @Nullable
        String transform(String group, String name, @Nullable String declaredVersion);
    }
}
