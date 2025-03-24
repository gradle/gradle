/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.deprecation.DeprecationData;
import org.gradle.api.problems.deprecation.source.ReportSource;
import org.gradle.api.problems.internal.AdditionalDataBuilder;

import javax.annotation.Nullable;

public class DefaultDeprecationData implements DeprecationData {

    private final ReportSource source;
    private final String removedIn;
    private final String replacedBy;

    public DefaultDeprecationData(ReportSource source, String removedIn, String replacedBy) {
        this.source = source;
        this.removedIn = removedIn;
        this.replacedBy = replacedBy;
    }

    @Nullable
    @Override
    public ReportSource getSource() {
        return source;
    }

    @Nullable
    @Override
    public String getRemovedIn() {
        return removedIn;
    }

    @Nullable
    @Override
    public String getReplacedBy() {
        return replacedBy;
    }

    public static AdditionalDataBuilder<? extends AdditionalData> builder(@Nullable DeprecationData input) {
        if (input == null) {
            return new DefaultDeprecationDataBuilder(null, null, null);
        } else {
            return new DefaultDeprecationDataBuilder(input.getSource(), input.getRemovedIn(), input.getReplacedBy());
        }
    }

}
