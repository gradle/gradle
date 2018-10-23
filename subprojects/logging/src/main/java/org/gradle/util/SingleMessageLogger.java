/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.util;

import com.google.common.annotations.VisibleForTesting;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.internal.Factory;
import org.gradle.internal.featurelifecycle.DeprecatedFeatureUsage;
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadaster;
import org.gradle.internal.featurelifecycle.FeatureHandler;
import org.gradle.internal.featurelifecycle.FeatureUsage;
import org.gradle.internal.featurelifecycle.IncubatingFeatureUsage;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.LoggingIncubatingFeatureHandler;
import org.gradle.internal.featurelifecycle.UsageLocationReporter;

import javax.annotation.Nullable;

import static org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler.getRemovalDetails;

@ThreadSafe
public class SingleMessageLogger {
    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    public static final String INCUBATION_MESSAGE = "%s is an incubating feature.";

    private static LoggingDeprecatedFeatureHandler deprecatedFeatureHandler = new LoggingDeprecatedFeatureHandler();
    private static LoggingIncubatingFeatureHandler incubatingFeatureHandler = new LoggingIncubatingFeatureHandler();

    public synchronized static void reset() {
        deprecatedFeatureHandler.reset();
        incubatingFeatureHandler.reset();
    }

    public synchronized static void init(UsageLocationReporter reporter, WarningMode warningMode, DeprecatedUsageBuildOperationProgressBroadaster buildOperationProgressBroadaster) {
        deprecatedFeatureHandler.init(reporter, warningMode, buildOperationProgressBroadaster);
    }

    public synchronized static void reportSuppressedDeprecations() {
        deprecatedFeatureHandler.reportSuppressedDeprecations();
    }

    public static void nagUserOfReplacedPlugin(String pluginName, String replacement) {
        if (isEnabled()) {
            nagUserOfDeprecatedPlugin(pluginName, String.format("Please use the %s plugin instead.", replacement));
        }
    }

    public static void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement) {
        if (isEnabled()) {
            nagUserOfDeprecatedPlugin(pluginName, String.format("Consider using the %s plugin instead.", replacement));
        }
    }

    public static void nagUserOfDeprecatedPlugin(String pluginName) {
        nagUserOfDeprecatedPlugin(pluginName, null);
    }

    public static void nagUserOfDeprecatedPlugin(String pluginName, @Nullable String advice) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s plugin has been deprecated.", pluginName),
                thisWillBeRemovedMessage(),
                advice,
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfToolReplacedWithExternalOne(String toolName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                "The %s has been deprecated.", toolName),
                thisWillBeRemovedMessage(),
                String.format("Consider using %s instead.", replacement),
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfReplacedTask(String taskName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s task has been deprecated.", taskName),
                thisWillBeRemovedMessage(), String.format("Please use the %s task instead.", replacement),
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfReplacedTaskType(String taskName, String replacement) {
        if (isEnabled()) {
            nagUserWith(
                String.format("The %s task type has been deprecated.", taskName),
                thisWillBeRemovedMessage(), String.format("Please use the %s instead.", replacement),
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfReplacedMethod(String methodName, String replacement) {
        if (isEnabled()) {
            nagUserWith(
                String.format("The %s method has been deprecated.", methodName), thisWillBeRemovedMessage(),
                String.format("Please use the %s method instead.", replacement),
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    /**
     * Use for a method that is not deprecated, but something about the method parameters or state is deprecated.
     */
    public static void nagUserOfDiscontinuedMethodInvocation(String invocation) {
        nagUserOfDiscontinuedMethodInvocation(invocation, null);
    }

    public static void nagUserOfDiscontinuedMethodInvocation(String invocation, String advice) {
        if (isEnabled()) {
            nagUserWith(
                    String.format("Using method %s has been deprecated.", invocation),
                    thisWillBecomeAnError(),
                    advice,
                    null,
                    DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    /**
     * Use for some operation that is not deprecated, but something about the method parameters or state is deprecated.
     */
    public static void nagUserOfDiscontinuedInvocation(String invocation) {
        if (isEnabled()) {
            nagUserWith(
                String.format("%s has been deprecated.", invocation),
                thisWillBecomeAnError(),
                null,
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    /**
     * Use for a method that is not deprecated, but something about the method parameters or state is deprecated.
     */
    public static void nagUserOfReplacedMethodInvocation(String invocation, String replacement) {
        if (isEnabled()) {
            nagUserWith(
                String.format("Using method %s has been deprecated.", invocation),
                thisWillBecomeAnError(),
                String.format(String.format("Please use the %s method instead.", replacement)),
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfReplacedProperty(String propertyName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                "The %s property has been deprecated.", propertyName), thisWillBeRemovedMessage(), String.format("Please use the %s property instead.", replacement), null, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfDiscontinuedMethod(String methodName) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s method has been deprecated.", methodName),
                thisWillBeRemovedMessage(),
                null,
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfDiscontinuedMethod(String methodName, String advice) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s method has been deprecated.", methodName),
                thisWillBeRemovedMessage(),
                advice,
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfDiscontinuedMethod(String methodName, String advice, String contextualAdvice) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s method has been deprecated.", methodName),
                thisWillBeRemovedMessage(),
                advice,
                contextualAdvice,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s property has been deprecated.", propertyName),
                thisWillBeRemovedMessage(),
                advice,
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s named parameter has been deprecated.", parameterName),
                thisWillBeRemovedMessage(),
                String.format("Please use the %s named parameter instead.", replacement),
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    /**
     * Try to avoid using this nagging method. The other methods use a consistent wording for when things will be removed.
     */
    public static void nagUserWithDeprecatedIndirectUserCodeCause(String summary) {
        if (isEnabled()) {
            nagUserWithDeprecatedIndirectUserCodeCause(summary, null);
        }
    }

    public static void nagUserWithDeprecatedBuildInvocationFeature(String summary, String advice) {
        nagUserWithDeprecatedBuildInvocationFeature(summary, thisWillBeRemovedMessage(), advice);
    }

    public static void nagUserWithDeprecatedBuildInvocationFeature(String summary, String removalDetails, String advice) {
        nagUserWith(String.format("%s has been deprecated.", summary), removalDetails, advice, null, DeprecatedFeatureUsage.Type.BUILD_INVOCATION);
    }

    public static void nagUserWithDeprecatedIndirectUserCodeCause(String summary, @Nullable String advice) {
        if (isEnabled()) {
            nagUserWithDeprecatedIndirectUserCodeCause(summary, advice, null);
        }
    }

    public static void nagUserWithDeprecatedIndirectUserCodeCause(String summary, @Nullable String advice, @Nullable String contextualAdvice) {
        if (isEnabled()) {
            nagUserWith(String.format("%s has been deprecated.", summary), thisWillBeRemovedMessage(), advice, contextualAdvice, DeprecatedFeatureUsage.Type.USER_CODE_INDIRECT);
        }
    }

    public static void nagUserWithDeprecatedIndirectUserCodeCause(String summary, String removalDetails, String advice, String contextualAdvice) {
        if (isEnabled()) {
            nagUserWith(String.format("%s has been deprecated.", summary), removalDetails, advice, contextualAdvice, DeprecatedFeatureUsage.Type.USER_CODE_INDIRECT);
        }
    }

    /**
     * Try to avoid using this nagging method. The other methods use a consistent wording for when things will be removed.
     */
    @VisibleForTesting
    static void nagUserWith(String summary) {
        if (isEnabled()) {
            nagUserWith(summary, null);
        }
    }

    /**
     * Try to avoid using this nagging method. The other methods use a consistent wording for when things will be removed.
     */
    public static void nagUserWith(String message, @Nullable String advice) {
        if (isEnabled()) {
            nagUserWith(message, thisWillBeRemovedMessage(), advice, null, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserWith(String summary, String removalDetails, @Nullable String advice, @Nullable String contextualAdvice) {
        if (isEnabled()) {
            nagUserWith(deprecatedFeatureHandler, new DeprecatedFeatureUsage(summary, removalDetails, advice, contextualAdvice, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, SingleMessageLogger.class));
        }
    }

    public static void nagUserWith(String summary, String removalDetails, @Nullable String advice, @Nullable String contextualAdvice, DeprecatedFeatureUsage.Type usageType) {
        if (isEnabled()) {
            nagUserWith(deprecatedFeatureHandler, new DeprecatedFeatureUsage(summary, removalDetails, advice, contextualAdvice, usageType, SingleMessageLogger.class));
        }
    }

    private synchronized static <T extends FeatureUsage> void nagUserWith(FeatureHandler<T> handler, T usage) {
        handler.featureUsed(usage);
    }

    /**
     * Avoid using this method, use the variant with an explanation instead.
     */
    public static void nagUserOfDeprecated(String thing) {
        if (isEnabled()) {
            nagUserWith(String.format("%s has been deprecated.", thing),
                thisWillBeRemovedMessage(),
                null,
                null,
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfDeprecated(String thing, String advice) {
        if (isEnabled()) {
            nagUserWith(String.format("%s has been deprecated.", thing), thisWillBeRemovedMessage(), advice, null, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfDeprecatedBehaviour(String behaviour) {
        if (isEnabled()) {
            nagUserWith(behaviour, String.format("This behaviour has been deprecated and %s", getRemovalDetails()), null, null, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    public static void nagUserOfDeprecatedThing(String thing) {
        nagUserOfDeprecatedThing(thing, null);
    }

    public static void nagUserOfDeprecatedThing(String thing, @Nullable String advice) {
        if (isEnabled()) {
            nagUserWith(thing, String.format("This has been deprecated and %s", getRemovalDetails()), advice, null, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT);
        }
    }

    @Nullable
    public static <T> T whileDisabled(Factory<T> factory) {
        ENABLED.set(false);
        try {
            return factory.create();
        } finally {
            ENABLED.set(true);
        }
    }

    public static void whileDisabled(Runnable action) {
        ENABLED.set(false);
        try {
            action.run();
        } finally {
            ENABLED.set(true);
        }
    }

    private static boolean isEnabled() {
        return ENABLED.get();
    }

    private static String thisWillBecomeAnError() {
        return String.format("This will fail with an error in Gradle %s.", GradleVersion.current().getNextMajor().getVersion());
    }

    private static String thisWillBeRemovedMessage() {
        return String.format("This %s", getRemovalDetails());
    }

    public static void incubatingFeatureUsed(String incubatingFeature) {
        nagUserWith(incubatingFeatureHandler, new IncubatingFeatureUsage(incubatingFeature, SingleMessageLogger.class));
    }
}
