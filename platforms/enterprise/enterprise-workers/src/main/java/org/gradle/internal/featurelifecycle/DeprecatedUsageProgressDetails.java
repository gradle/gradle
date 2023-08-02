/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.featurelifecycle;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A usage of some deprecated API or feature.
 *
 * @since 4.10
 */
public interface DeprecatedUsageProgressDetails {

    /**
     * See {@code org.gradle.internal.deprecation.DeprecatedFeatureUsage#getSummary()}
     */
    String getSummary();

    /**
     * See {@code org.gradle.internal.deprecation.DeprecatedFeatureUsage#getRemovalDetails()}
     */
    String getRemovalDetails();

    /**
     * See {@code org.gradle.internal.deprecation.DeprecatedFeatureUsage#getAdvice()}
     */
    @Nullable
    String getAdvice();

    /**
     * See {@code org.gradle.internal.deprecation.DeprecatedFeatureUsage#getContextualAdvice()}
     */
    @Nullable
    String getContextualAdvice();

    /**
     * See {@code org.gradle.internal.deprecation.DeprecatedFeatureUsage#getDocumentationUrl()}
     *
     * @since 6.2
     */
    @Nullable
    String getDocumentationUrl();

    /**
     * See {@code org.gradle.internal.deprecation.DeprecatedFeatureUsage#getType()}.
     *
     * Value is always of {@code org.gradle.internal.deprecation.DeprecatedFeatureUsage.Type#name()}.
     */
    String getType();

    /**
     * See {@code org.gradle.internal.deprecation.DeprecatedFeatureUsage#getStack()}
     */
    List<StackTraceElement> getStackTrace();

}
