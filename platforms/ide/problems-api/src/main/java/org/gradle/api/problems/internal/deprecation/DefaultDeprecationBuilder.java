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
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.deprecation.DeprecateGenericSpec;
import org.gradle.api.problems.deprecation.DeprecateMethodSpec;
import org.gradle.api.problems.deprecation.DeprecatePluginSpec;
import org.gradle.api.problems.deprecation.DeprecationDataSpec;
import org.gradle.api.problems.internal.InternalProblemBuilder;

import javax.annotation.Nullable;

class DefaultDeprecationBuilder implements DeprecateGenericSpec, DeprecatePluginSpec, DeprecateMethodSpec {
    private final InternalProblemBuilder builder;

    public DefaultDeprecationBuilder(InternalProblemBuilder builder) {
        this.builder = builder;
    }

    @Override
    public DefaultDeprecationBuilder replacedBy(final String replacement) {
        builder.additionalData(DeprecationDataSpec.class, new Action<DeprecationDataSpec>() {
            @Override
            public void execute(DeprecationDataSpec deprecationDataSpec) {
                deprecationDataSpec.replacedBy(replacement);
            }
        });
        return this;
    }

    @Override
    public DefaultDeprecationBuilder removedInVersion(final String opaqueVersion) {
        builder.additionalData(DeprecationDataSpec.class, new Action<DeprecationDataSpec>() {
            @Override
            public void execute(DeprecationDataSpec deprecationDataSpec) {
                deprecationDataSpec.removedIn(new DefaultOpaqueDeprecatedVersion(opaqueVersion));
            }
        });
        return this;
    }

    @Override
    public DeprecateGenericSpec removedInVersion(final Integer major, @Nullable final Integer minor, @Nullable final Integer patch, @Nullable final String qualifier) {
        builder.additionalData(DeprecationDataSpec.class, new Action<DeprecationDataSpec>() {
            @Override
            public void execute(DeprecationDataSpec deprecationDataSpec) {
                deprecationDataSpec.removedIn(new DefaultSemverDeprecatedVersion(major, minor, patch, qualifier));
            }
        });
        return this;
    }

    @Override
    public DefaultDeprecationBuilder because(final String reason) {
        builder.additionalData(DeprecationDataSpec.class, new Action<DeprecationDataSpec>() {
            @Override
            public void execute(DeprecationDataSpec deprecationDataSpec) {
                deprecationDataSpec.because(reason);
            }
        });
        return this;
    }

    @Override
    public DefaultDeprecationBuilder withDetails(String details) {
        builder.details(details);
        return this;
    }

    public InternalProblemBuilder getProblemBuilder() {
        return builder;
    }

    public Problem build() {
        return builder.build();
    }
}
