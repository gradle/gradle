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

import org.gradle.api.problems.deprecation.DeprecatedVersion;

import javax.annotation.Nullable;

class DefaultDeprecationData implements DeprecationData {

    private final DeprecatedVersion removedIn;
    private final String replacedBy;
    private final String reason;

    public DefaultDeprecationData(@Nullable DeprecatedVersion removedIn, @Nullable String because, @Nullable String reason) {
        this.removedIn = removedIn;
        this.replacedBy = because;
        this.reason = reason;
    }

    @Override
    @Nullable
    public DeprecatedVersion getRemovedIn() {
        return removedIn;
    }

    @Override
    @Nullable
    public String getReplacedBy() {
        return replacedBy;
    }

    @Override
    @Nullable
    public String getBecause() {
        return reason;
    }

    static class Builder implements DeprecationDataSpec {
        private DeprecatedVersion removedIn;
        private String replacedBy;
        private String reason;

        @Override
        public DeprecationDataSpec removedIn(DeprecatedVersion version) {
            this.removedIn = version;
            return this;
        }

        @Override
        public DeprecationDataSpec replacedBy(String reason) {
            this.replacedBy = reason;
            return this;
        }

        @Override
        public DeprecationDataSpec because(String reason) {
            this.reason = reason;
            return this;
        }

        public DeprecationData build() {
            return new DefaultDeprecationData(removedIn, replacedBy, reason);
        }
    }

}
