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
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.Factory;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.problems.buildtree.ProblemStream;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;


/**
 * Provides entry points for constructing and emitting deprecation messages.
 * The basic deprecation message structure is "Summary. DeprecationTimeline. Context. Advice. Documentation."
 * <p>
 * The deprecateX methods in this class return a builder that guides creation of the deprecation message.
 * Summary is populated by the deprecateX methods in this class.
 * Context can be added in free text using {@link DeprecationMessageBuilder#withContext(String)}.
 * Advice is constructed contextually using {@link DeprecationMessageBuilder.WithReplacement#replaceWith(Object)} methods based on the thing being deprecated.
 * Alternatively, it can be populated using {@link DeprecationMessageBuilder#withAdvice(String)}.
 * <p>
 * DeprecationTimeline is mandatory and is added using one of:
 * <ul>
 *   <li>{@link DeprecationMessageBuilder#willBeRemovedInGradle9()}
 *   <li>{@link DeprecationMessageBuilder#willBecomeAnErrorInGradle9()}
 * </ul>
 * <p>
 * After DeprecationTimeline is set, Documentation reference must be added using one of:
 * <ul>
 *   <li>{@link DeprecationMessageBuilder.WithDeprecationTimeline#withUpgradeGuideSection(int, String)}
 *   <li>{@link DeprecationMessageBuilder.WithDeprecationTimeline#withDslReference(Class, String)}
 *   <li>{@link DeprecationMessageBuilder.WithDeprecationTimeline#withUserManual(String, String)}
 * </ul>
 *
 * In order for the deprecation message to be emitted, terminal operation {@link DeprecationMessageBuilder.WithDocumentation#nagUser()} has to be called after one of the documentation providing methods.
 */
@ThreadSafe
public class DeprecationLogger {

    /**
     * Counts the levels of nested {@code whileDisabled} invocations.
     */
    private static final ThreadLocal<Integer> DISABLE_COUNT = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private static final LoggingDeprecatedFeatureHandler DEPRECATED_FEATURE_HANDLER = new LoggingDeprecatedFeatureHandler();

    public synchronized static void init(WarningMode warningMode, BuildOperationProgressEventEmitter buildOperationProgressEventEmitter, InternalProblems problemsService, ProblemStream problemStream) {
        DEPRECATED_FEATURE_HANDLER.init(warningMode, buildOperationProgressEventEmitter, problemsService, problemStream);
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
        return new ExplicitDeprecationMessageBuilder(feature);
    }

    /**
     * Indirect usage means that stack trace at the time the deprecation is logged does not indicate the call site.
     * This directs GE to not display unhelpful stacktraces with the deprecation.
     * <p>
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
     * Output: ${behaviour}. This behavior is deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateBehaviour deprecateBehaviour(String behaviour) {
        return new DeprecationMessageBuilder.DeprecateBehaviour(behaviour);
    }

    /**
     * Output: ${behaviour}. This behavior is deprecated. ${advice}
     */
    @CheckReturnValue
    @SuppressWarnings("rawtypes")
    public static DeprecationMessageBuilder.WithDeprecationTimeline warnOfChangedBehaviour(final String behaviour, final String advice) {
        return new DeprecationMessageBuilder.WithDeprecationTimeline(new DeprecationMessageBuilder() {
            @Override
            DeprecationMessage build() {
                return new DeprecationMessage(behaviour + ". This behavior is deprecated.", "", advice, null, null, DeprecatedFeatureUsage.Type.USER_CODE_INDIRECT, problemIdDisplayName, problemId);
            }
        }); // TODO: it is not ok that NO_DOCUMENTATION is hardcoded here
    }

    /**
     * Output: ${action} has been deprecated.
     */
    @CheckReturnValue
    @SuppressWarnings("rawtypes")
    public static DeprecationMessageBuilder.DeprecateAction deprecateAction(final String action) {
        return new DeprecationMessageBuilder.DeprecateAction(action);
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

    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateType deprecateType(Class<?> type) {
        return new DeprecationMessageBuilder.DeprecateType(type.getCanonicalName());
    }

    /**
     * Output: The ${task} task has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateTask deprecateTask(String task) {
        return new DeprecationMessageBuilder.DeprecateTask(task);
    }

    /**
     * Output: The task type ${task.getCanonicalName()} (used by the ${task.getPath()} task) has been deprecated.
     */
    @CheckReturnValue
    public static DeprecationMessageBuilder.DeprecateTaskType deprecateTaskType(Class<?> task, String path) {
        return new DeprecationMessageBuilder.DeprecateTaskType(task.getCanonicalName(), path);
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
            DeprecatedFeatureUsage featureUsage = deprecationMessage.toDeprecatedFeatureUsage(calledFrom);
            nagUserWith(featureUsage);

            if (!featureUsage.formattedMessage().contains("deprecated")) {
                throw new RuntimeException("Deprecation message does not contain the word 'deprecated'. Message: \n" + featureUsage.formattedMessage());
            }
        }
    }

    @Nullable
    public static <T> T whileDisabled(Factory<T> factory) {
        disable();
        try {
            return factory.create();
        } finally {
            maybeEnable();
        }
    }

    public static void whileDisabled(Runnable action) {
        disable();
        try {
            action.run();
        } finally {
            maybeEnable();
        }
    }

    public static <T, E extends Exception> T whileDisabledThrowing(ThrowingFactory<T, E> factory) {
        disable();
        try {
            return toUncheckedThrowingFactory(factory).create();
        } finally {
            maybeEnable();
        }
    }

    public static <E extends Exception> void whileDisabledThrowing(ThrowingRunnable<E> runnable) {
        disable();
        try {
            toUncheckedThrowingRunnable(runnable).run();
        } finally {
           maybeEnable();
        }
    }

    private static void disable() {
        DISABLE_COUNT.set(DISABLE_COUNT.get() + 1);
    }

    private static void maybeEnable() {
        DISABLE_COUNT.set(DISABLE_COUNT.get() - 1);
    }

    private static boolean isEnabled() {
        // log deprecation messages only after the outermost whileDisabled finished execution
        return DISABLE_COUNT.get() == 0;
    }

    public interface ThrowingFactory<T, E extends Exception> {
        T create() throws E;
    }

    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }

    /**
     * Turns a {@link ThrowingFactory} into a {@link Factory}.
     * The compiler is happy with the casting that allows to hide the checked exception.
     * The runtime is happy with the casting because the checked exception type information is captured in a generic type parameter which gets erased.
     */
    private static <T, E extends Exception> Factory<T> toUncheckedThrowingFactory(final ThrowingFactory<T, E> throwingFactory) {
        return new Factory<T>() {
            @Nullable
            @Override
            public T create() {
                @SuppressWarnings("unchecked")
                ThrowingFactory<T, RuntimeException> factory = (ThrowingFactory<T, RuntimeException>) throwingFactory;
                return factory.create();
            }
        };
    }

    /**
     * Turns a {@link ThrowingRunnable} into a {@link Runnable}.
     *
     * @see #toUncheckedThrowingFactory(ThrowingFactory)
     */
    private static <E extends Exception> Runnable toUncheckedThrowingRunnable(final ThrowingRunnable<E> throwingRunnable) {
        return new Runnable() {
            @Override
            public void run() {
                @SuppressWarnings("unchecked")
                ThrowingRunnable<RuntimeException> runnable = (ThrowingRunnable<RuntimeException>) throwingRunnable;
                runnable.run();
            }
        };
    }

    private synchronized static void nagUserWith(DeprecatedFeatureUsage usage) {
        DEPRECATED_FEATURE_HANDLER.featureUsed(usage);
    }

    private static class ExplicitDeprecationMessageBuilder extends DeprecationMessageBuilder<ExplicitDeprecationMessageBuilder> {
        private final String feature;

        public ExplicitDeprecationMessageBuilder(String feature) {
            this.feature = feature;
            setSummary(feature + " has been deprecated.");
        }

        @Override
        DeprecationMessage build() {
            if(problemId == null) {
                setProblemId(createDefaultDeprecationId(feature));
            }
            return super.build();
        }
    }
}
