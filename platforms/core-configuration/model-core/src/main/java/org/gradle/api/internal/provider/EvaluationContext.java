/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.gradle.api.GradleException;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class keeps track of all providers being evaluated at the moment.
 * It helps to provide nicer error messages when the evaluation enters an endless cycle (A obtains a value of B which obtains a value of A).
 * <p>
 * Concurrent evaluation of same providers by multiple threads is still allowed.
 * <p>
 * This class is thread-safe, but the returned contexts must be closed by the same thread that opens them.
 */
public final class EvaluationContext {
    /**
     * An evaluation that runs with some provider being added to the evaluation context.
     *
     * @param <R> the return type
     * @param <E> (optional) exception type being thrown by the evaluation
     */
    @FunctionalInterface
    public interface ScopedEvaluation<R, E extends Exception> {
        R evaluate() throws E;
    }

    /**
     * A scope context. One can obtain an instance by calling {@link #enter(ProviderInternal)}.
     * Closing this context removes the provider from the evaluation context.
     * The primary use case is to serve as an argument for the try-with-resources block.
     * <p>
     * It is not safe to call {@link #close()} multiple times.
     * The instances of the class may not be unique for different providers being added.
     * <p>
     * Contexts must be closed in the order they are obtained.
     * This context must be closed by the same thread that obtained it.
     */
    public interface ScopeContext extends AutoCloseable {
        /**
         * Removes the provider added to evaluation context when obtaining this class from the context.
         */
        @Override
        void close();
    }

    private static final int EXPECTED_MAX_CONTEXT_SIZE = 64;

    private static final EvaluationContext INSTANCE = new EvaluationContext();

    private final ThreadLocal<PerThreadContext> threadLocalContext = ThreadLocal.withInitial(() -> new PerThreadContext(null));

    /**
     * Returns the current instance of EvaluationContext for this thread.
     *
     * @return the evaluation context
     */
    public static EvaluationContext current() {
        return INSTANCE;
    }

    private EvaluationContext() {}

    /**
     * Adds the provider to the set of "evaluating" providers and returns the context instance to remove it from there upon closing.
     * This method is intended to be used in the try-with-resources block's initializer.
     */
    public ScopeContext enter(ProviderInternal<?> provider) {
        return getContext().enter(provider);
    }

    /**
     * Runs the {@code evaluation} with the {@code provider} being marked as "evaluating".
     * If the provider is already being evaluated, throws {@link CircularEvaluationException}.
     *
     * @param provider the provider to evaluate
     * @param evaluation the evaluation
     * @param <R> the type of the result
     * @param <E> (optional) exception type being thrown by the evaluation
     * @return the result of the evaluation
     * @throws E exception from the {@code evaluation} is propagated
     * @throws CircularEvaluationException if the provider is currently being evaluated in the outer scope
     */
    public <R, E extends Exception> R evaluate(ProviderInternal<?> provider, ScopedEvaluation<? extends R, E> evaluation) throws E {
        try (ScopeContext ignored = enter(provider)) {
            return evaluation.evaluate();
        }
    }

    /**
     * Runs the {@code evaluation} with the {@code provider} being marked as "evaluating".
     * If the provider is already being evaluated, returns {@code fallbackValue}.
     * <p>
     * Note that fallback value is not used if the evaluation itself throws {@link CircularEvaluationException}, the exception propagates instead.
     *
     * @param provider the provider to evaluate
     * @param fallbackValue the fallback value to return if the provider is already evaluating
     * @param evaluation the evaluation
     * @param <R> the type of the result
     * @param <E> (optional) exception type being thrown by the evaluation
     * @return the result of the evaluation
     * @throws E exception from the {@code evaluation} is propagated
     */
    public <R, E extends Exception> R tryEvaluate(ProviderInternal<?> provider, R fallbackValue, ScopedEvaluation<? extends R, E> evaluation) throws E {
        if (getContext().isInScope(provider)) {
            return fallbackValue;
        }
        // It is possible that the downstream chain itself forms a cycle.
        // However, it should be its responsibility to be defined in terms of safe evaluation rather than us intercepting the failure here.
        return evaluate(provider, evaluation);
    }

    /**
     * Runs the {@code evaluation} in a nested evaluation context. A nested context allows to re-enter evaluation of the providers that are being evaluated in the enclosed context.
     * <p>
     * Use sparingly. In most cases, it is better to rework the call chain to avoid re-evaluating the provider.
     *
     * @param evaluation the evaluation
     * @param <R> the type of the result
     * @param <E> (optional) exception type being thrown by the evaluation
     * @return the result of the evaluation
     * @throws E exception from the {@code evaluation} is propagated
     */
    public <R, E extends Exception> R evaluateNested(ScopedEvaluation<? extends R, E> evaluation) throws E {
        try (ScopeContext ignored = nested()) {
            return evaluation.evaluate();
        }
    }

    private PerThreadContext getContext() {
        return threadLocalContext.get();
    }

    private PerThreadContext setContext(PerThreadContext newContext) {
        threadLocalContext.set(newContext);

        return newContext;
    }

    private ScopeContext nested() {
        return setContext(new PerThreadContext(getContext()));
    }

    private final class PerThreadContext implements ScopeContext {
        private final Set<ProviderInternal<?>> providersInScope = new ReferenceOpenHashSet<>(EXPECTED_MAX_CONTEXT_SIZE);
        private final List<ProviderInternal<?>> providersStack = new ReferenceArrayList<>(EXPECTED_MAX_CONTEXT_SIZE);
        @Nullable
        private final PerThreadContext parent;

        public PerThreadContext(@Nullable PerThreadContext parent) {
            this.parent = parent;
        }

        private void push(ProviderInternal<?> provider) {
            if (providersInScope.add(provider)) {
                providersStack.add(provider);
            } else {
                throw prepareException(provider);
            }
        }

        private void pop() {
            ProviderInternal<?> removed = providersStack.remove(providersStack.size() - 1);
            providersInScope.remove(removed);
        }

        public PerThreadContext enter(ProviderInternal<?> owner) {
            push(owner);
            return this;
        }

        @Override
        public void close() {
            // Closing the "nested" context.
            if (parent != null && providersStack.isEmpty()) {
                setContext(parent);
                return;
            }
            pop();
        }

        public boolean isInScope(ProviderInternal<?> provider) {
            return providersInScope.contains(provider);
        }

        private CircularEvaluationException prepareException(ProviderInternal<?> circular) {
            int i = providersStack.indexOf(circular);
            assert i >= 0;
            List<ProviderInternal<?>> preCycleList = providersStack.subList(i, providersStack.size());
            ImmutableList<ProviderInternal<?>> evaluationCycle = ImmutableList.<ProviderInternal<?>>builderWithExpectedSize(preCycleList.size() + 1)
                .addAll(preCycleList)
                .add(circular)
                .build();
            return new CircularEvaluationException(evaluationCycle);
        }
    }

    /**
     * An exception caused by the circular evaluation.
     */
    public static class CircularEvaluationException extends GradleException {
        private final ImmutableList<ProviderInternal<?>> evaluationCycle;

        CircularEvaluationException(List<ProviderInternal<?>> evaluationCycle) {
            this.evaluationCycle = ImmutableList.copyOf(evaluationCycle);
        }

        @Override
        public String getMessage() {
            return "Circular evaluation detected: " + formatEvaluationChain(evaluationCycle);
        }

        /**
         * Returns the evaluation cycle.
         * The list represents a "stack" of providers currently being evaluated, and is at least two elements long.
         * The first and last elements of the list are the same provider.
         *
         * @return the evaluation cycle as a list
         */
        public List<ProviderInternal<?>> getEvaluationCycle() {
            return evaluationCycle;
        }

        private static String formatEvaluationChain(List<ProviderInternal<?>> evaluationCycle) {
            try (ScopeContext ignored = current().nested()) {
                return evaluationCycle.stream()
                    .map(CircularEvaluationException::safeToString)
                    .collect(Collectors.joining("\n -> "));
            }
        }

        /**
         * Computes {@code ProviderInternal.toString()}, but swallows all thrown exceptions.
         */
        private static String safeToString(ProviderInternal<?> providerInternal) {
            try {
                return providerInternal.toString();
            } catch (Throwable e) {
                // Calling e.getMessage() here can cause infinite recursion.
                // It happens if e is CircularEvaluationException itself, because getMessage calls formatEvaluationChain.
                // It can also happen for some other custom exceptions that wrap CircularEvaluationException and call its getMessage inside their.
                // This is why we resort to losing the information and only providing exception class.
                // A well-behaved toString should not throw anyway.
                return providerInternal.getClass().getName() + " (toString failed with " + e.getClass() + ")";
            }
        }
    }
}
