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

package org.gradle.api.problems.internal.deprecation;

import org.gradle.api.Action;
import org.gradle.api.problems.deprecation.CommonDeprecationSpec;
import org.gradle.api.problems.deprecation.DeprecationDataSpec;
import org.gradle.api.problems.internal.InternalProblemBuilder;

import javax.annotation.Nullable;

/**
 * Default implementation of {@link CommonDeprecationSpec},
 * implementing common builder elements for deprecations.
 *
 * @param <T> the type of the sub-builder used
 */
// This is a necessary suppression because we need to pretend that we are returning the sub-builder's type
// This is achieved by casting to T, but this of course is an unchecked cast
@SuppressWarnings("unchecked")
class DefaultCommonDeprecationBuilder<T extends CommonDeprecationSpec<?>> implements CommonDeprecationSpec<T> {

    protected final InternalProblemBuilder builder;

    public DefaultCommonDeprecationBuilder(InternalProblemBuilder builder) {
        this.builder = builder;
    }

    @Override
    public T replacedBy(final String replacement) {
        builder.additionalData(
            DeprecationDataSpec.class,
            new Action<DeprecationDataSpec>() {
                @Override
                public void execute(DeprecationDataSpec deprecationDataSpec) {
                    deprecationDataSpec.replacedBy(replacement);
                }
            }
        );
        return (T)this;
    }

    @Override
    public T removedInVersion(final String opaqueVersion) {
        builder.additionalData(
            DeprecationDataSpec.class,
            new Action<DeprecationDataSpec>() {
                @Override
                public void execute(DeprecationDataSpec deprecationDataSpec) {
                    deprecationDataSpec.removedIn(new DefaultOpaqueDeprecatedVersion(opaqueVersion));
                }
            }
        );
        return (T)this;
    }

    @Override
    public T removedInVersion(final Integer major, @Nullable final Integer minor, @Nullable final String patch) {
        builder.additionalData(
            DeprecationDataSpec.class,
            new Action<DeprecationDataSpec>() {
                @Override
                public void execute(DeprecationDataSpec deprecationDataSpec) {
                    deprecationDataSpec.removedIn(new DefaultSemverDeprecatedVersion(major, minor, patch));
                }
            }
        );
        return (T)this;
    }

    @Override
    public T because(final String reason) {
        builder.additionalData(
            DeprecationDataSpec.class,
            new Action<DeprecationDataSpec>() {
                @Override
                public void execute(DeprecationDataSpec deprecationDataSpec) {
                    deprecationDataSpec.because(reason);
                }
            }
        );
        return (T)this;
    }

    @Override
    public T withDetails(String details) {
        builder.details(details);
        return (T)this;
    }

}
