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

import org.gradle.internal.deprecation.DeprecatedFeatureUsage;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A usage of some deprecated API or feature.
 *
 * @since 4.10
 */
@UsedByScanPlugin
public interface DeprecatedUsageProgressDetails {

    /**
     * See {@link DeprecatedFeatureUsage#getSummary()}
     */
    String getSummary();

    /**
     * See {@link DeprecatedFeatureUsage#getRemovalDetails()}
     */
    String getRemovalDetails();

    /**
     * See {@link DeprecatedFeatureUsage#getAdvice()}
     */
    @Nullable
    String getAdvice();

    /**
     * See {@link DeprecatedFeatureUsage#getContextualAdvice()}
     */
    @Nullable
    String getContextualAdvice();

    /**
     * See {@link DeprecatedFeatureUsage#getDocumentationUrl()}
     *
     * @since 6.2
     */
    @Nullable
    String getDocumentationUrl();

    /**
     * See {@link DeprecatedFeatureUsage#getType()}.
     *
     * Value is always of {@link DeprecatedFeatureUsage.Type#name()}.
     */
    String getType();

    /**
     * See {@link DeprecatedFeatureUsage#getStack()}
     */
    List<StackTraceElement> getStackTrace();

}
