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

import org.gradle.api.problems.deprecation.DeprecationData;
import org.gradle.api.problems.deprecation.ReportSource;
import org.gradle.api.problems.internal.AdditionalDataBuilder;

import javax.annotation.Nullable;

public class DefaultDeprecationDataBuilder implements DeprecationDataSpec, AdditionalDataBuilder<DeprecationData> {

    private ReportSource source;
    private String removedIn;
    private String replacedBy;
    private String because;

    public DefaultDeprecationDataBuilder(
        @Nullable ReportSource source,
        @Nullable String removedIn,
        @Nullable String replacedBy,
        @Nullable String because
    ) {
        this.source = source;
        this.removedIn = removedIn;
        this.replacedBy = replacedBy;
        this.because = because;
    }

    @Override
    public void reportSource(ReportSource source) {
        this.source = source;
    }

    @Override
    public void replacedBy(String replacement) {
        this.replacedBy = replacement;
    }

    @Override
    public void removedIn(String version) {
        this.removedIn = version;
    }

    @Override
    public void because(String reason) {
        this.because = reason;
    }

    @Override
    public DeprecationData build() {
        return new DefaultDeprecationData(
            source,
            removedIn,
            replacedBy,
            because
        );
    }

}
