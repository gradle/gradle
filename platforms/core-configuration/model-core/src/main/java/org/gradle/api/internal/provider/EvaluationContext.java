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
 * This class keeps track of all objects being evaluated at the moment.
 * It helps to provide nicer error messages when the evaluation enters an endless cycle (A obtains a value of B which obtains a value of A).
 * <p>
 * Concurrent evaluation of same objects by multiple threads is still allowed.
 * <p>
 * This class is thread-safe, but the returned contexts must be closed by the same thread that opens them.
 */
public final class EvaluationContext {
    /**
     * An evaluation that runs with the owner being added to the evaluation context.
     *
     * @param <R> the return type
     * @param <E> (optional) exception type being thrown by the evaluation
     */
    @FunctionalInterface
    public interface ScopedEvaluation<R, E extends Exception> {
        R evaluate() throws E;
    }

    /**
     * A marker interface for types that can be evaluating.
     */
    public interface EvaluationOwner {}

    /**
     * A scope context. One can obtain an instance by calling {@link #open(EvaluationOwner)}.
     * Closing this context removes the owner from the evaluation context.
     * The primary use case is to serve as an argument for the try-with-resources block.
     * It can also serve as a token parameter of a method that must be executed within such a block.
     * <p>
     * It is not safe to call {@link #close()} multiple times.
     * The instances of the class may not be unique for different owners being added.
     * <p>
     * Contexts must be closed in the order they are obtained.
     * This context must be closed by the same thread that obtained it.
     */
    public interface ScopeContext extends AutoCloseable {
        /**
         * Returns the owner of the current scope, which is the last object that started its evaluation.
         * Can be null if the current scope has no owner (e.g. a just opened nested context).
         *
         * @return the owner
         */
        @Nullable
        EvaluationOwner getOwner();

        /**
         * Opens a nested context. A nested context allows to re-enter evaluation of the objects that are being evaluated in this context.
         * The newly returned nested context has no owner.
         * <p>
         * This method may be marginally more effective than using {@link EvaluationContext#evaluateNested(ScopedEvaluation)}.
         *
         * @return the nested context, to close it when done
         */
        ScopeContext nested();

        /**
         * Removes the owner added to evaluation context when obtaining this class from the context.
         * Must be called exactly once for every {@link #open(EvaluationOwner)} or {@link #nested()} call.
         * Prefer to use try-with-resource instead of calling this method.
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
     * Adds the owner to the set of "evaluating" objects and returns the context instance to remove it from there upon closing.
     * This method is intended to be used in the try-with-resources block's initializer.
     */
    public ScopeContext open(EvaluationOwner owner) {
        return getContext().open(owner);
    }

    /**
     * Runs the {@code evaluation} with the {@code owner} being marked as "evaluating".
     * If the owner is already being evaluated, throws {@link CircularEvaluationException}.
     *
     * @param owner the object to evaluate
     * @param evaluation the evaluation
     * @param <R> the type of the result
     * @param <E> (optional) exception type being thrown by the evaluation
     * @return the result of the evaluation
     * @throws E exception from the {@code evaluation} is propagated
     * @throws CircularEvaluationException if the owner is currently being evaluated in the outer scope
     */
    public <R, E extends Exception> R evaluate(EvaluationOwner owner, ScopedEvaluation<? extends R, E> evaluation) throws E {
        try (ScopeContext ignored = open(owner)) {
            return evaluation.evaluate();
        }
    }

    /**
     * Runs the {@code evaluation} with the {@code owner} being marked as "evaluating".
     * If the owner is already being evaluated, returns {@code fallbackValue}.
     * <p>
     * Note that fallback value is not used if the evaluation itself throws {@link CircularEvaluationException}, the exception propagates instead.
     *
     * @param owner the owner to evaluate
     * @param fallbackValue the fallback value to return if the owner is already evaluating
     * @param evaluation the evaluation
     * @param <R> the type of the result
     * @param <E> (optional) exception type being thrown by the evaluation
     * @return the result of the evaluation
     * @throws E exception from the {@code evaluation} is propagated
     */
    public <R, E extends Exception> R tryEvaluate(EvaluationOwner owner, R fallbackValue, ScopedEvaluation<? extends R, E> evaluation) throws E {
        if (getContext().isInScope(owner)) {
            return fallbackValue;
        }
        // It is possible that the downstream chain itself forms a cycle.
        // However, it should be its responsibility to be defined in terms of safe evaluation rather than us intercepting the failure here.
        return evaluate(owner, evaluation);
    }

    /**
     * Runs the {@code evaluation} in a nested evaluation context. A nested context allows to re-enter evaluation of the objects that are being evaluated in the enclosed context.
     * <p>
     * Use sparingly. In most cases, it is better to rework the call chain to avoid re-evaluating the owner.
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
        return getContext().nested();
    }

    private final class PerThreadContext implements ScopeContext {
        private final Set<EvaluationOwner> objectsInScope = new ReferenceOpenHashSet<>(EXPECTED_MAX_CONTEXT_SIZE);
        private final List<EvaluationOwner> evaluationStack = new ReferenceArrayList<>(EXPECTED_MAX_CONTEXT_SIZE);
        @Nullable
        private final PerThreadContext parent;

        public PerThreadContext(@Nullable PerThreadContext parent) {
            this.parent = parent;
        }

        private void push(EvaluationOwner owner) {
            if (objectsInScope.add(owner)) {
                evaluationStack.add(owner);
            } else {
                throw prepareException(owner);
            }
        }

        private void pop() {
            EvaluationOwner removed = evaluationStack.remove(evaluationStack.size() - 1);
            objectsInScope.remove(removed);
        }

        public PerThreadContext open(EvaluationOwner owner) {
            push(owner);
            return this;
        }

        @Override
        public ScopeContext nested() {
            return setContext(new PerThreadContext(this));
        }

        @Override
        public void close() {
            // Closing the "nested" context.
            if (parent != null && evaluationStack.isEmpty()) {
                setContext(parent);
                return;
            }
            pop();
        }

        public boolean isInScope(EvaluationOwner owner) {
            return objectsInScope.contains(owner);
        }

        @Override
        @Nullable
        public EvaluationOwner getOwner() {
            if (evaluationStack.isEmpty()) {
                return null;
            }
            return evaluationStack.get(evaluationStack.size() - 1);
        }

        private CircularEvaluationException prepareException(EvaluationOwner circular) {
            int i = evaluationStack.indexOf(circular);
            assert i >= 0;
            List<EvaluationOwner> preCycleList = evaluationStack.subList(i, evaluationStack.size());
            ImmutableList<EvaluationOwner> evaluationCycle = ImmutableList.<EvaluationOwner>builderWithExpectedSize(preCycleList.size() + 1)
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
        private final ImmutableList<EvaluationOwner> evaluationCycle;

        CircularEvaluationException(List<EvaluationOwner> evaluationCycle) {
            this.evaluationCycle = ImmutableList.copyOf(evaluationCycle);
        }

        @Override
        public String getMessage() {
            return "Circular evaluation detected: " + formatEvaluationChain(evaluationCycle);
        }

        /**
         * Returns the evaluation cycle.
         * The list represents a "stack" of owners currently being evaluated, and is at least two elements long.
         * The first and last elements of the list are the same owner.
         *
         * @return the evaluation cycle as a list
         */
        public List<EvaluationOwner> getEvaluationCycle() {
            return evaluationCycle;
        }

        private static String formatEvaluationChain(List<EvaluationOwner> evaluationCycle) {
            try (ScopeContext ignored = current().nested()) {
                return evaluationCycle.stream()
                    .map(CircularEvaluationException::safeToString)
                    .collect(Collectors.joining("\n -> "));
            }
        }

        /**
         * Computes {@code Object.toString()}, but swallows all thrown exceptions.
         */
        private static String safeToString(Object owner) {
            try {
                return owner.toString();
            } catch (Throwable e) {
                // Calling e.getMessage() here can cause infinite recursion.
                // It happens if e is CircularEvaluationException itself, because getMessage calls formatEvaluationChain.
                // It can also happen for some other custom exceptions that wrap CircularEvaluationException and call its getMessage inside their.
                // This is why we resort to losing the information and only providing exception class.
                // A well-behaved toString should not throw anyway.
                return owner.getClass().getName() + " (toString failed with " + e.getClass() + ")";
            }
        }
    }
}
