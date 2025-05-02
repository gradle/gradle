/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.caching;

public class CachingDisabledReason {
    private final CachingDisabledReasonCategory category;
    private final String message;

    public CachingDisabledReason(CachingDisabledReasonCategory category, String message) {
        this.category = category;
        this.message = message;
    }

    public CachingDisabledReasonCategory getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", message, category);
    }
}
