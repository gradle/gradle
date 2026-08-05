/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.evaluation;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import org.jspecify.annotations.Nullable;

/**
 * This class keeps track of all objects being evaluated at the moment.
 * It helps to provide nicer error messages when the evaluation enters an endless cycle (A obtains a value of B which obtains a value of A).
 * <p>
 * Concurrent evaluation of same objects by multiple threads is still allowed.
 * <p>
 * This class is thread-safe, but the returned contexts must be closed by the same thread that opens them.
 */
public final class EvaluationContext {
    private static final EvaluationContext INSTANCE = new EvaluationContext();

    /**
     * Intentionally not using {@link ThreadLocal#withInitial}, for two reasons:
     * <ul>
     * <li>Threads that only query the current owner must not pay for instantiating and
     * storing a context just to observe that nothing is being evaluated.</li>
     * <li>Every {@code withInitial} thread local in the JVM invokes its supplier through a
     * single call site inside {@code ThreadLocal.SuppliedThreadLocal}. The JIT sees many
     * unrelated supplier implementations at that call site and cannot inline or devirtualize
     * the dispatch, so initialization always goes through a slow virtual call, and heavy
     * traffic from other suppliers can trigger deoptimization of code this call site was
     * inlined into.</li>
     * </ul>
     * The context is instead created lazily by {@link #getContext()}, a call site this class
     * owns exclusively.
     */
    private static final ThreadLocal<@Nullable PerThreadContext> THREAD_LOCAL_CONTEXT = new ThreadLocal<>();

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
    public EvaluationScopeContext open(EvaluationOwner owner) {
        return getContext().open(owner);
    }

    /**
     * Returns the "topmost" evaluation owner or null if nothing is being evaluated right now.
     */
    @Nullable
    public EvaluationOwner getOwner() {
        PerThreadContext context = THREAD_LOCAL_CONTEXT.get();
        return context == null ? null : context.getOwner();
    }

    /**
     * Returns whether an evaluation is in progress in the current thread.
     */
    public boolean isEvaluating() {
        return getOwner() != null;
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
        try (EvaluationScopeContext ignored = open(owner)) {
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
        try (EvaluationScopeContext ignored = nested()) {
            return evaluation.evaluate();
        }
    }

    private PerThreadContext getContext() {
        PerThreadContext context = THREAD_LOCAL_CONTEXT.get();
        if (context == null) {
            context = new PerThreadContext(null);
            THREAD_LOCAL_CONTEXT.set(context);
        }
        return context;
    }

    private PerThreadContext setContext(PerThreadContext newContext) {
        THREAD_LOCAL_CONTEXT.set(newContext);

        return newContext;
    }

    EvaluationScopeContext nested() {
        return getContext().nested();
    }

    /**
     * Uses a simple identity-based array stack.
     *
     * Cycle detection scans the array linearly, which is fast for the typical
     * shallow evaluation depths (< 10). Automatically resizes to accommodate
     * deeper stacks.
     */
    private final class PerThreadContext implements EvaluationScopeContext {
        /**
         * This was chosen by looking at the typical stack sizes on the
         * Gradle build. We're trying to balance resizing vs wasted space.
         *
         * Most stacks were small (only 1 element). The biggest stack was 19 elements.
         *
         * This size represented about 85% of all contexts, so Gradle does
         * no resizing in those cases.
         *
         * The extreme case still requires 2 resizings.
         */
        private static final int INITIAL_CAPACITY = 8;

        private final ReferenceArrayList<EvaluationOwner> stack;

        @Nullable
        private final PerThreadContext parent;

        public PerThreadContext(@Nullable PerThreadContext parent) {
            this.stack = new ReferenceArrayList<>(INITIAL_CAPACITY);
            this.parent = parent;
        }

        public PerThreadContext open(EvaluationOwner owner) {
            // Identity scan for cycle detection
            if (isInScope(owner)) {
                throw prepareException(owner);
            }
            stack.add(owner);
            return this;
        }

        @Override
        public EvaluationScopeContext nested() {
            return setContext(new PerThreadContext(this));
        }

        @Override
        public void close() {
            // Closing the "nested" context.
            if (parent != null && stack.isEmpty()) {
                setContext(parent);
                return;
            }
            stack.pop();
        }

        public boolean isInScope(EvaluationOwner owner) {
            return indexOf(owner) != -1;
        }

        @Override
        @Nullable
        public EvaluationOwner getOwner() {
            if (stack.isEmpty()) {
                return null;
            }
            return stack.top();
        }

        private CircularEvaluationException prepareException(EvaluationOwner circular) {
            int i = indexOf(circular);
            assert i >= 0;
            ReferenceList<EvaluationOwner> path = stack.subList(i, stack.size());
            ImmutableList.Builder<EvaluationOwner> builder = ImmutableList.builderWithExpectedSize(path.size() + 1);
            builder.addAll(path);
            builder.add(circular);
            return new CircularEvaluationException(builder.build());
        }

        private int indexOf(EvaluationOwner owner) {
            return stack.indexOf(owner);
        }
    }

}
