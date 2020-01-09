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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.internal.Factory;
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadaster;
import org.gradle.internal.featurelifecycle.FeatureHandler;
import org.gradle.internal.featurelifecycle.FeatureUsage;
import org.gradle.internal.featurelifecycle.IncubatingFeatureUsage;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.LoggingIncubatingFeatureHandler;
import org.gradle.internal.featurelifecycle.UsageLocationReporter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler.getRemovalDetails;
import static org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler.getWillBecomeErrorMessage;

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

    /**
     * Output format:
     * <p>
     * The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} plugin instead.
     */
    public static void nagUserOfReplacedPlugin(String pluginName, String replacement) {
        if (isEnabled()) {
            nagUserOfDeprecatedPlugin(pluginName, String.format("Please use the %s plugin instead.", replacement));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X. Consider using the ${replacement} plugin instead.
     */
    public static void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement) {
        if (isEnabled()) {
            nagUserOfDeprecatedPlugin(pluginName, String.format("Consider using the %s plugin instead.", replacement));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X.
     */
    public static void nagUserOfDeprecatedPlugin(String pluginName) {
        nagUserOfDeprecatedPlugin(pluginName, null);
    }

    /**
     * Output format:
     * <p>
     * The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X. Consult the upgrading guide for further information: link-to-user-manual/upgrading_version_${majorVersion}.html#${replacement}
     */
    public static void nagUserOfDeprecatedPlugin(String pluginName, int majorVersion, String upgradeGuideSection) {
        nagUserOfDeprecatedPlugin(pluginName, "Consult the upgrading guide for further information: " +
            new DocumentationRegistry().getDocumentationFor("upgrading_version_" + majorVersion, upgradeGuideSection));
    }

    /**
     * Output format:
     * <p>
     * The ${pluginName} plugin has been deprecated. This is scheduled to be removed in Gradle X. ${advice}
     */
    public static void nagUserOfDeprecatedPlugin(String pluginName, @Nullable String advice) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s plugin has been deprecated.", pluginName), thisWillBeRemovedMessage())
                    .withAdvice(advice));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${toolName} has been deprecated. This is scheduled to be removed in Gradle X. Consider using ${replacement} instead.
     */
    public static void nagUserOfToolReplacedWithExternalOne(String toolName, String replacement) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s has been deprecated.", toolName), thisWillBeRemovedMessage())
                    .withAdvice(String.format("Consider using %s instead.", replacement)));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${taskName} task has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} task instead.
     */
    public static void nagUserOfReplacedTask(String taskName, String replacement) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s task has been deprecated.", taskName), thisWillBeRemovedMessage())
                    .withAdvice(String.format("Please use the %s task instead.", replacement)));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${taskName} task type has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} instead.
     */
    public static void nagUserOfReplacedTaskType(String taskName, String replacement) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s task type has been deprecated.", taskName), thisWillBeRemovedMessage())
                    .withAdvice(String.format("Please use the %s instead.", replacement)));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${methodName} method has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} method instead.
     */
    public static void nagUserOfReplacedMethod(String methodName, String replacement) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s method has been deprecated.", methodName), thisWillBeRemovedMessage())
                    .withAdvice(String.format("Please use the %s method instead.", replacement)));
        }
    }

    /**
     * Output format:
     * <p>
     * Using method ${invocation} has been deprecated. This will fail with an error in Gradle X. ${advice}
     */
    public static void nagUserOfDiscontinuedMethodInvocation(String invocation, String advice) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("Using method %s has been deprecated.", invocation), thisWillBecomeAnError()).withAdvice(advice));
        }
    }

    /**
     * Use for some operation that is not deprecated, but something about the method parameters or state is deprecated.
     * Output format:
     * <p>
     * Using method ${invocation} has been deprecated. This will fail with an error in Gradle X.
     */
    public static void nagUserOfDiscontinuedInvocation(String invocation) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("%s has been deprecated.", invocation), thisWillBecomeAnError()));
        }
    }

    /**
     * Use for a method that is not deprecated, but something about the method parameters or state is deprecated.
     * Output format:
     * <p>
     * Using method ${invocation} has been deprecated. This will fail with an error in Gradle X. Please use the ${replacement} method instead.
     */
    public static void nagUserOfReplacedMethodInvocation(String invocation, String replacement) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("Using method %s has been deprecated.", invocation), thisWillBecomeAnError())
                    .withAdvice(String.format("Please use the %s method instead.", replacement)));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${methodName} method has been deprecated. This is scheduled to be removed in Gradle X.
     */
    public static void nagUserOfDiscontinuedMethod(String methodName) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s method has been deprecated.", methodName), thisWillBeRemovedMessage()));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${methodName} method has been deprecated. This is scheduled to be removed in Gradle X. ${advice}
     */
    public static void nagUserOfDiscontinuedMethod(String methodName, String advice) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s method has been deprecated.", methodName), thisWillBeRemovedMessage()).withAdvice(advice));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${methodName} method has been deprecated. This is scheduled to be removed in Gradle X. ${contextualAdvice} ${advice}
     */
    public static void nagUserOfDiscontinuedMethod(String methodName, String advice, String contextualAdvice) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s method has been deprecated.", methodName), thisWillBeRemovedMessage())
                    .withAdvice(advice)
                    .withContextualAdvice(contextualAdvice));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${propertyName} property has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} property instead.
     */
    public static void nagUserOfReplacedProperty(String propertyName, String replacement) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s property has been deprecated.", propertyName), thisWillBeRemovedMessage())
                    .withAdvice(String.format("Please use the %s property instead.", replacement)));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${propertyName} property has been deprecated. This is scheduled to be removed in Gradle X. ${advice}
     */
    public static void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s property has been deprecated.", propertyName), thisWillBeRemovedMessage()).withAdvice(advice));
        }
    }

    /**
     * Output format:
     * <p>
     * The ${parameterName} named parameter has been deprecated. This is scheduled to be removed in Gradle X. Please use the ${replacement} named parameter instead.
     */
    public static void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("The %s named parameter has been deprecated.", parameterName), thisWillBeRemovedMessage())
                    .withAdvice(String.format("Please use the %s named parameter instead.", replacement)));
        }
    }

    /**
     * Output format:
     * <p>
     * ${summary} has been deprecated. This is scheduled to be removed in Gradle X. ${advice}
     */
    public static void nagUserWithDeprecatedBuildInvocationFeature(String summary, String advice) {
        nagUserWithDeprecatedBuildInvocationFeature(summary, thisWillBeRemovedMessage(), advice);
    }


    /**
     * Output format:
     * <p>
     * ${summary} has been deprecated. ${removeDetails} ${advice}
     */
    public static void nagUserWithDeprecatedBuildInvocationFeature(String summary, String removalDetails, String advice) {
        nagUserWith(new DeprecationMessage(String.format("%s has been deprecated.", summary), removalDetails).withAdvice(advice).withBuildInvocation());
    }

    /**
     * Output format:
     * <p>
     * ${summary} has been deprecated. This is scheduled to be removed in Gradle X. ${advice}
     */
    public static void nagUserWithDeprecatedIndirectUserCodeCause(String summary, @Nullable String advice) {
        if (isEnabled()) {
            nagUserWithDeprecatedIndirectUserCodeCause(summary, advice, null);
        }
    }

    /**
     * Try to avoid using this nagging method. The other methods use a consistent wording for when things will be removed.
     * Output format:
     * <p>
     * ${summary} has been deprecated. This is scheduled to be removed in Gradle X.
     */
    public static void nagUserWithDeprecatedIndirectUserCodeCause(String summary) {
        if (isEnabled()) {
            nagUserWithDeprecatedIndirectUserCodeCause(summary, null);
        }
    }

    /**
     * Output format:
     * <p>
     * ${summary} has been deprecated. This is scheduled to be removed in Gradle X. ${contextualAdvice} ${advice}
     */
    public static void nagUserWithDeprecatedIndirectUserCodeCause(String summary, @Nullable String advice, @Nullable String contextualAdvice) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("%s has been deprecated.", summary), thisWillBeRemovedMessage())
                    .withAdvice(advice)
                    .withContextualAdvice(contextualAdvice)
                    .withIndirectUsage());
        }
    }

    /**
     * Output format:
     * <p>
     * ${summary} has been deprecated. ${removeDetails} ${contextualAdvice} ${advice}
     */
    public static void nagUserWithDeprecatedIndirectUserCodeCause(String summary, String removalDetails, String advice, String contextualAdvice) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(String.format("%s has been deprecated.", summary), removalDetails)
                .withAdvice(advice)
                .withContextualAdvice(contextualAdvice)
                .withIndirectUsage());
        }
    }

    /**
     * Output format:
     * <p>
     * ${behaviour} This has been deprecated and is scheduled to be removed in Gradle {X}.
     */
    public static void nagUserOfDeprecatedBehaviour(String behaviour) {
        if (isEnabled()) {
            nagUserWith(new DeprecationMessage(behaviour, String.format("This behaviour has been deprecated and %s", getRemovalDetails())));
        }
    }

    public enum ConfigurationDeprecationType {
        DEPENDENCY_DECLARATION("use", true),
        CONSUMPTION("use attributes to consume", false),
        RESOLUTION("resolve", true),
        ARTIFACT_DECLARATION("use", true);

        public final String usage;
        public final boolean inUserCode;

        ConfigurationDeprecationType(String usage, boolean inUserCode) {
            this.usage = usage;
            this.inUserCode = inUserCode;
        }

        public String displayName() {
            return name().toLowerCase().replace('_', ' ');
        }
    }

    public static void nagUserWith(DeprecationMessage deprecationMessage) {
        if (isEnabled()) {
            nagUserWith(deprecatedFeatureHandler, deprecationMessage.toDeprecatedFeatureUsage(SingleMessageLogger.class));
        }
    }

    private synchronized static <T extends FeatureUsage> void nagUserWith(FeatureHandler<T> handler, T usage) {
        handler.featureUsed(usage);
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
        return getWillBecomeErrorMessage();
    }

    private static String thisWillBeRemovedMessage() {
        return String.format("This %s", getRemovalDetails());
    }

    public static void incubatingFeatureUsed(String incubatingFeature) {
        nagUserWith(incubatingFeatureHandler, new IncubatingFeatureUsage(incubatingFeature, SingleMessageLogger.class));
    }

    @Nullable
    public static Throwable getDeprecationFailure() {
        return deprecatedFeatureHandler.getDeprecationFailure();
    }
}
