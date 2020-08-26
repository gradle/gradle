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

import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.internal.Factory;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.UsageLocationReporter;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;


/**
 * Provides entry points for constructing and emitting deprecation messages.
 * The basic deprecation message structure is "Summary. DeprecationTimeline. Context. Advice. Documentation."
 *
 * The deprecateX methods in this class return a builder that guides creation of the deprecation message.
 * Summary is populated by the deprecateX methods in this class.
 * Context can be added in free text using {@link DeprecationMessageBuilder#withContext(String)}.
 * Advice is constructed contextually using {@link DeprecationMessageBuilder.WithReplacement#replaceWith(Object)} methods based on the thing being deprecated. Alternatively, it can be populated using {@link DeprecationMessageBuilder#withAdvice(String)}.
 * DeprecationTimeline is mandatory and is added using one of:
 * - ${@link DeprecationMessageBuilder#willBeRemovedInGradle7()}
 * - ${@link DeprecationMessageBuilder#willBecomeAnErrorInGradle7()}
 * After DeprecationTimeline is set, Documentation reference must be added using one of:
 * - {@link DeprecationMessageBuilder.WithDeprecationTimeline#withUpgradeGuideSection(int, String)}
 * - {@link DeprecationMessageBuilder.WithDeprecationTimeline#withDslReference(Class, String)}
 * - {@link DeprecationMessageBuilder.WithDeprecationTimeline#withUserManual(String, String)}
 *
 * In order for the deprecation message to be emitted, terminal operation {@link DeprecationMessageBuilder.WithDocumentation#nagUser()} has to be called after one of the documentation providing methods.
 */
@ThreadSafe
public class DeprecationLogger {

    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    private static final LoggingDeprecatedFeatureHandler DEPRECATED_FEATURE_HANDLER = new LoggingDeprecatedFeatureHandler();

    public synchronized static void init(UsageLocationReporter reporter, WarningMode warningMode, BuildOperationProgressEventEmitter buildOperationProgressEventEmitter) {
        DEPRECATED_FEATURE_HANDLER.init(reporter, warningMode, buildOperationProgressEventEmitter);
    }

    public synchronized static void reset() {
        DEPRECATED_FEATURE_HANDLER.reset();
    }

    public synchronized static void reportSuppressedDeprecations() {
        DEPRECATED_FEATURE_HANDLER.reportSuppressedDeprecations();
    }

    @Nullable
    public static Throwable getDeprecationFailure() {
        return DEPRECATED_FEATURE_HANDLER.getDeprecationFailure();
    }

    /**
     * This is a rather generic deprecation entry point - consider using a more specific deprecation method and only resort to this one if there is no fit.
     *
     * Output: ${feature} has been deprecated.
     */
    @CheckReturnValue
    @SuppressWarnings("rawtypes")
    public static DeprecationMessageBuilder<?> deprecate(final String feature) {
        return new DeprecationMessageBuilder() {
            @Override
            DeprecationMessage build() {
                setSummary(xHasBeenDeprecated(feature));
                return super.build();
            }
        };
    }

    /**
     * Indirect usage means that stack trace at the time the deprecation is logged does not indicate the call site.
     * This directs GE to not display unhelpful stacktraces with the deprecation.
     *
     * Output: ${feature} has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder<?> deprecateIndirectUsage(String feature) {
        DeprecationMessageBuilder<?> builder = deprecate(feature);
        builder.setIndirectUsage();
        return builder;
    }

    /**
     * Output: ${feature} has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder<?> deprecateBuildInvocationFeature(String feature) {
        DeprecationMessageBuilder<?> builder = deprecate(feature);
        builder.setBuildInvocationUsage();
        return builder;
    }

    /**
     * Output: ${behaviour}.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateBehaviour deprecateBehaviour(String behaviour) {
        return new DeprecationMessageBuilder.DeprecateBehaviour(behaviour);
    }

    /**
     * Output: ${behaviour} ${advice}
     */
    @CheckReturnValue
    @SuppressWarnings("rawtypes")
    public static DeprecationMessageBuilder.WithDeprecationTimeline warnOfChangedBehaviour(final String behaviour, final String advice) {
        return new DeprecationMessageBuilder.WithDeprecationTimeline(new DeprecationMessageBuilder() {
            @Override
            DeprecationMessage build() {
                return new DeprecationMessage(behaviour, "", advice, null, Documentation.NO_DOCUMENTATION, DeprecatedFeatureUsage.Type.USER_CODE_INDIRECT);
            }
        });
    }

    /**
     * Output: ${action} has been deprecated.
     */
    @CheckReturnValue
    @SuppressWarnings("rawtypes")
    public static DeprecationMessageBuilder<?> deprecateAction(final String action) {
        return new DeprecationMessageBuilder() {
            @Override
            DeprecationMessage build() {
                setSummary(xHasBeenDeprecated(action));
                return super.build();
            }
        };
    }

    /**
     * Output: The ${property} property has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateProperty deprecateProperty(Class<?> propertyClass, String property) {
        return new DeprecationMessageBuilder.DeprecateProperty(propertyClass, property);
    }

    /**
     * Output: The ${property} system property has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateSystemProperty deprecateSystemProperty(String systemProperty) {
        return new DeprecationMessageBuilder.DeprecateSystemProperty(systemProperty);
    }

    /**
     * Output: The ${parameter} named parameter has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateNamedParameter deprecateNamedParameter(String parameter) {
        return new DeprecationMessageBuilder.DeprecateNamedParameter(parameter);
    }

    /**
     * Output: The ${method} method has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateMethod deprecateMethod(Class<?> methodClass, String methodWithParams) {
        return new DeprecationMessageBuilder.DeprecateMethod(methodClass, methodWithParams);
    }

    /**
     * Output: Using method ${invocation} has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateInvocation deprecateInvocation(String methodWithParams) {
        return new DeprecationMessageBuilder.DeprecateInvocation(methodWithParams);
    }

    /**
     * Output: The ${task} task has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateTask deprecateTask(String task) {
        return new DeprecationMessageBuilder.DeprecateTask(task);
    }

    /**
     * Output: The ${plugin} plugin has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecatePlugin deprecatePlugin(String plugin) {
        return new DeprecationMessageBuilder.DeprecatePlugin(plugin);
    }

    /**
     * Output: Internal API ${api} has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateInternalApi deprecateInternalApi(String api) {
        return new DeprecationMessageBuilder.DeprecateInternalApi(api);
    }

    /**
     * Output: The ${configurationType} configuration has been deprecated for ${declarationType}.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.ConfigurationDeprecationTypeSelector deprecateConfiguration(String configurationType) {
        return new DeprecationMessageBuilder.ConfigurationDeprecationTypeSelector(configurationType);
    }

    static void nagUserWith(DeprecationMessageBuilder<?> deprecationMessageBuilder, Class<?> calledFrom) {
        if (isEnabled()) {
            DeprecationMessage deprecationMessage = deprecationMessageBuilder.build();
            nagUserWith(deprecationMessage.toDeprecatedFeatureUsage(calledFrom));
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

    private synchronized static void nagUserWith(DeprecatedFeatureUsage usage) {
        DEPRECATED_FEATURE_HANDLER.featureUsed(usage);
    }

    private static String xHasBeenDeprecated(String x) {
        return String.format("%s has been deprecated.", x);
    }

}
