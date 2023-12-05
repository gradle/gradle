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
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.gradle.api.GradleException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EvaluationContext {
    @FunctionalInterface
    public interface ScopedEvaluation<R, E extends Exception> {
        R evaluate() throws E;
    }

    private static final int EXPECTED_MAX_CONTEXT_SIZE = 64;

    private static final EvaluationContext INSTANCE = new EvaluationContext();

    private final ThreadLocal<PerThreadContext> threadLocalContext = ThreadLocal.withInitial(() -> new PerThreadContext(null));

    // TODO(mlopatkin) Replace with injection.
    public static EvaluationContext current() {
        return INSTANCE;
    }

    private EvaluationContext() {}

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
        try (ScopeContext ignored = getContext().enter(provider)) {
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

    private PerThreadContext getContext() {
        return threadLocalContext.get();
    }

    private PerThreadContext setContext(PerThreadContext newContext) {
        threadLocalContext.set(newContext);

        return newContext;
    }

    public interface ScopeContext extends AutoCloseable {
        @Override
        void close();
    }

    public ScopeContext enter(ProviderInternal<?> provider) {
        return getContext().enter(provider);
    }

    private ScopeContext nested() {
        return setContext(new PerThreadContext(getContext()));
    }

    private final class PerThreadContext implements ScopeContext {
        private final Set<ProviderInternal<?>> providersInScope = new ReferenceOpenHashSet<>(EXPECTED_MAX_CONTEXT_SIZE);
        private final List<ProviderInternal<?>> providersStack = new ArrayList<>(EXPECTED_MAX_CONTEXT_SIZE);
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

    public static class CircularEvaluationException extends GradleException {
        private final ImmutableList<ProviderInternal<?>> evaluationCycle;

        CircularEvaluationException(List<ProviderInternal<?>> evaluationCycle) {
            this.evaluationCycle = ImmutableList.copyOf(evaluationCycle);
        }

        @Override
        public String getMessage() {
            return "Circular evaluation detected: " + formatEvaluationChain(evaluationCycle);
        }

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
                // Calling e.getMessage() can cause infinite recursion. It happens if e is CircularEvaluationException itself, but
                // can also happen for some other custom exception that happens to call this method.
                // User code should not be able to trigger this kind of circularity.
                return providerInternal.getClass().getName() + " (toString failed with " + e.getClass() + ")";
            }
        }
    }
}
