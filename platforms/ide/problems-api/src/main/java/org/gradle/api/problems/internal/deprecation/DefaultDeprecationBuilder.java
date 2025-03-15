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
import org.gradle.api.problems.deprecation.DeprecateMethodSpec;
import org.gradle.api.problems.deprecation.DeprecatePluginSpec;
import org.gradle.api.problems.deprecation.DeprecateSpec;
import org.gradle.api.problems.deprecation.ReportSource;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblemBuilder;

class DefaultDeprecationBuilder implements DeprecateSpec, DeprecatePluginSpec, DeprecateMethodSpec {

    private final InternalProblemBuilder builder;

    public DefaultDeprecationBuilder(final ReportSource source, InternalProblemBuilder builder) {
        this.builder = builder;
        builder.additionalDataInternal(
            DeprecationDataSpec.class,
            new Action<DeprecationDataSpec>() {
                @Override
                public void execute(DeprecationDataSpec deprecationDataSpec) {
                    deprecationDataSpec.reportSource(source);
                }
            }
        );
    }

    @Override
    public DefaultDeprecationBuilder replacedBy(final String replacement) {
        builder.additionalDataInternal(
            DeprecationDataSpec.class,
            new Action<DeprecationDataSpec>() {
                @Override
                public void execute(DeprecationDataSpec deprecationDataSpec) {
                    deprecationDataSpec.replacedBy(replacement);
                }
            }
        );
        return this;
    }

    @Override
    public DefaultDeprecationBuilder removedInVersion(final String version) {
        builder.additionalDataInternal(
            DeprecationDataSpec.class,
            new Action<DeprecationDataSpec>() {
                @Override
                public void execute(DeprecationDataSpec deprecationDataSpec) {
                    deprecationDataSpec.removedIn(version);
                }
            }
        );
        return this;
    }

    @Override
    public DefaultDeprecationBuilder because(final String reason) {
        builder.additionalDataInternal(
            DeprecationDataSpec.class,
            new Action<DeprecationDataSpec>() {
                @Override
                public void execute(DeprecationDataSpec deprecationDataSpec) {
                    deprecationDataSpec.because(reason);
                }
            }
        );
        return this;
    }

    public InternalProblemBuilder getProblemBuilder() {
        return builder;
    }

    public InternalProblem build() {
        return builder.build();
    }
}
