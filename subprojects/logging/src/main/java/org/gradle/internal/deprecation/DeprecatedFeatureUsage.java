/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.deprecation;

import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.featurelifecycle.FeatureUsage;

import javax.annotation.Nullable;

public class DeprecatedFeatureUsage extends FeatureUsage {

    private final String removalDetails;
    private final String advice;
    private final String contextualAdvice;
    private final Documentation documentation;

    private final Type type;

    public DeprecatedFeatureUsage(
        String summary,
        String removalDetails,
        @Nullable String advice,
        @Nullable String contextualAdvice,
        Documentation documentation,
        Type type,
        Class<?> calledFrom
    ) {
        super(summary, calledFrom);
        this.removalDetails = Preconditions.checkNotNull(removalDetails);
        this.advice = advice;
        this.contextualAdvice = contextualAdvice;
        this.type = Preconditions.checkNotNull(type);
        this.documentation = Preconditions.checkNotNull(documentation);
    }

    DeprecatedFeatureUsage(DeprecatedFeatureUsage usage, Exception traceException) {
        super(usage.getSummary(), usage.getCalledFrom(), traceException);
        this.removalDetails = usage.removalDetails;
        this.advice = usage.advice;
        this.contextualAdvice = usage.contextualAdvice;
        this.documentation = usage.documentation;
        this.type = usage.type;
    }

    /**
     * Indicates the type of usage, affecting the feedback that can be given.
     */
    public enum Type {

        /**
         * The key characteristic is that the trace to the usage indicates the offending user code.
         *
         * Example: calling a deprecated method.
         */
        USER_CODE_DIRECT,

        /**
         * The key characteristic is that the trace to the usage DOES NOT indicate the offending user code,
         * but the usage happens during runtime and may be associated to a logical entity (e.g. task, plugin).
         *
         * The association between a usage and entity is not modelled by the usage,
         * but can be inferred from the operation stream (for deprecations, for which operation progress events are emitted).
         *
         * Example: annotation processor on compile classpath (feature is used at compile, not classpath definition)
         */
        USER_CODE_INDIRECT,

        /**
         * The key characteristic is that there is no useful “where was it used information”,
         * as the usage relates to how/where Gradle was invoked.
         *
         * Example: deprecated CLI switch.
         */
        BUILD_INVOCATION
    }

    /**
     * When the feature will be removed, and how if relevant.
     *
     * Example: This feature will be removed in Gradle 10.0.
     */
    public String getRemovalDetails() {
        return removalDetails;
    }

    /**
     * General, non usage specific, advice on what to do about this notice.
     *
     * Example: Use method Foo.baz() instead.
     */
    @Nullable
    public String getAdvice() {
        return advice;
    }

    /**
     * Advice on what to do about the notice, specific to this usage.
     *
     * Example: Annotation processors Foo, Bar and Baz were found on the compile classpath.
     */
    @Nullable
    public String getContextualAdvice() {
        return contextualAdvice;
    }

    /**
     * Link to documentation, describing how to migrate from this deprecated usage.
     *
     * Example: https://docs.gradle.org/current/userguide/upgrading_version_5.html#plugin_validation_changes
     *
     * @since 6.2
     */
    @Nullable
    public String getDocumentationUrl() {
        return documentation.documentationUrl();
    }

    public DeprecatedFeatureUsage.Type getType() {
        return type;
    }

    @Override
    public String formattedMessage() {
        StringBuilder outputBuilder = new StringBuilder(getSummary());
        append(outputBuilder, removalDetails);
        append(outputBuilder, contextualAdvice);
        append(outputBuilder, advice);
        append(outputBuilder, documentation.consultDocumentationMessage());
        return outputBuilder.toString();
    }

    private void append(StringBuilder outputBuilder, String message) {
        if (!StringUtils.isEmpty(message)) {
            outputBuilder.append(" ").append(message);
        }
    }

}
