/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.model;

import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.work.Synchronizer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Manages the transition between states of some object with mutable state.
 *
 * Adds validation to ensure that the object is in an expected state and applies thread safety.
 */
@ThreadSafe
public class StateTransitionController<T extends StateTransitionController.State> {
    private final DisplayName displayName;
    private final Synchronizer synchronizer;
    // This structure is immutable, and this field is mutated only by the thread that owns the lock
    private volatile CurrentState<T> state;

    public StateTransitionController(DisplayName displayName, T initialState, Synchronizer synchronizer) {
        this.displayName = displayName;
        this.synchronizer = synchronizer;
        this.state = new InState<>(displayName, initialState, null);
    }

    /**
     * Verifies that the current state is not the given state. Ignores any transition in progress and failures of previous operations.
     *
     * <p>You should try to not use this method, as it does not provide any thread safety for the code that follows the call.</p>
     */
    public void assertNotInState(T forbidden) {
        if (state.state == forbidden) {
            throw new IllegalStateException(displayName.getCapitalizedDisplayName() + " should not be in state " + forbidden + ".");
        }
    }

    /**
     * Verifies that the current state is the given state or some later state. Ignores any transition in progress and failures of previous operations.
     *
     * <p>You should try to not use this method, as it does not provide any thread safety for the code that follows the call.</p>
     */
    public void assertInStateOrLater(T expected) {
        CurrentState<T> current = state;
        if (!current.hasSeenStateIgnoringTransitions(expected)) {
            throw new IllegalStateException(displayName.getCapitalizedDisplayName() + " should be in state " + expected + " or later.");
        }
    }

    /**
     * Calculates a value when the current state is not the given state. Allows concurrent access to the state and does not block other threads from transitioning the state.
     * Fails if the current state is the given state or if a transition to the given state is happening or a previous transition has failed.
     *
     * <p>You should try to not use this method, as it does not provide full thread safety.</p>
     */
    public <S> S notInStateIgnoreOtherThreads(T forbidden, Supplier<S> supplier) {
        CurrentState<T> current = state;
        current.asResult().rethrow();
        current.assertNotInState(forbidden);
        try {
            return supplier.get();
        } catch (Throwable t) {
            // TODO - remove the need for locking here
            synchronizer.withLock(() -> {
                state = state.failed(ExecutionResult.failed(t));
            });
            throw UncheckedException.throwAsUncheckedException(t);
        }
    }

    /**
     * Runs the given action, verifying the current state is the expected state.
     * Fails if the current state is not the given state or a previous operation has failed.
     * Blocks until other operations are complete.
     */
    public void inState(T expected, Runnable action) {
        inState(expected, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs the given action, verifying the current state is the expected state.
     * Fails if the current state is not the given state, the current thread is transitioning the state, or a previous operation has failed.
     * Blocks until other operations are complete.
     */
    public <S> S inState(T expected, Supplier<S> action) {
        return synchronizer.withLock(() -> {
            CurrentState<T> current = state;
            current.assertInState(expected);
            try {
                return action.get();
            } catch (Throwable t) {
                state = current.failed(ExecutionResult.failed(t));
                throw state.rethrow();
            }
        });
    }

    /**
     * Runs the given action, verifying the current state is not the forbidden state.
     * Fails if the current state is the given state, the current thread is transitioning the state, or a previous operation has failed.
     * Blocks until other operations are complete.
     */
    public <S> S notInState(T forbidden, Supplier<S> action) {
        return synchronizer.withLock(() -> {
            CurrentState<T> current = state;
            current.assertNotInState(forbidden);
            try {
                return action.get();
            } catch (Throwable t) {
                state = current.failed(ExecutionResult.failed(t));
                throw state.rethrow();
            }
        });
    }

    /**
     * Resets the state to the given state.
     * Fails if the current state is not the given state, ignores failures from previous operations.
     * Blocks until other operations are complete.
     */
    public void restart(T fromState, T toState, Runnable action) {
        synchronizer.withLock(() -> {
            CurrentState<T> current = state;
            current.assertCanTransition(fromState, toState, true);
            action.run();
            state = new InState<>(displayName, toState, null);
        });
    }

    /**
     * Transitions to the given "to" state.
     * Fails if the current state is not the given "from" state, the current thread is transitioning the state, or a previous operation has failed.
     * Blocks until other operations are complete.
     */
    public void transition(T fromState, T toState, Runnable action) {
        synchronizer.withLock(() -> doTransition(fromState, toState, action));
    }

    /**
     * Transitions to the given "to" state.
     * Fails if the current state is not the given "from" state, the current thread is transitioning the state, or a previous operation has failed.
     * Blocks until other operations are complete.
     */
    public <S> S transition(T fromState, T toState, Supplier<? extends S> action) {
        return synchronizer.withLock(() -> doTransition(fromState, toState, () -> ExecutionResult.succeeded(action.get())).getValueOrRethrow());
    }

    /**
     * Transitions to the given "to" state, returning any failure in the result object.
     * Fails if the current state is not the given "from" state, the current thread is transitioning the state, or a previous operation has failed.
     */
    public ExecutionResult<Void> tryTransition(T fromState, T toState, Supplier<ExecutionResult<Void>> action) {
        return synchronizer.withLock(() -> doTransition(fromState, toState, action));
    }

    /**
     * Transitions to the given "to" state. Does nothing if the current state is the "to" state.
     * Fails if the current state is not either of the given "to" or "from" state, the current thread is currently transitioning the state, or a previous operation has failed.
     * Blocks until other operations are complete.
     */
    public void maybeTransition(T fromState, T toState, Runnable action) {
        synchronizer.withLock(() -> {
            if (state.inStateAndNotTransitioning(toState)) {
                return;
            }
            doTransition(fromState, toState, action);
        });
    }

    public void maybeTransitionIfNotCurrentlyTransitioning(T fromState, T toState, Runnable action) {
        synchronizer.withLock(() -> {
            if (state.inStateOrTransitioningTo(toState)) {
                return;
            }
            doTransition(fromState, toState, action);
        });
    }

    /**
     * Transitions to the given "to" state. Does nothing if the "to" state has already been transitioned to at some point in the past (but is not necessarily the current state).
     * Fails if the current state is not the given "from" state, the current thread is currently transitioning the state, or a previous operation has failed.
     * Blocks until other operations are complete.
     */
    public void transitionIfNotPreviously(T fromState, T toState, Runnable action) {
        synchronizer.withLock(() -> {
            if (state.hasSeenStateAndNotTransitioning(toState)) {
                return;
            }
            doTransition(fromState, toState, action);
        });
    }

    /**
     * Transitions to a final state, taking any failures from previous transitions and transforming them.
     */
    public ExecutionResult<Void> transition(T fromState, T toState, Function<ExecutionResult<Void>, ExecutionResult<Void>> action) {
        return synchronizer.withLock(() -> {
            CurrentState<T> current = state;
            current.assertCanTransition(fromState, toState, true);
            return doTransitionWithFailures(toState, action, current);
        });
    }

    public ExecutionResult<Void> transition(List<T> fromStates, T toState, Function<ExecutionResult<Void>, ExecutionResult<Void>> action) {
        return synchronizer.withLock(() -> {
            CurrentState<T> current = state;
            current.assertCanTransition(fromStates, toState, true);
            return doTransitionWithFailures(toState, action, current);
        });
    }

    private ExecutionResult<Void> doTransitionWithFailures(T toState, Function<ExecutionResult<Void>, ExecutionResult<Void>> action, CurrentState<T> current) {
        ExecutionResult<Void> currentResult = current.asResult();
        state = current.transitioningTo(toState);
        ExecutionResult<Void> result;
        try {
            result = action.apply(currentResult);
        } catch (Throwable t) {
            result = ExecutionResult.failed(t);
        }
        if (!result.getFailures().isEmpty()) {
            state = state.failed(result);
        } else {
            state = state.nextState(toState);
        }
        return result;
    }

    private void doTransition(T fromState, T toState, Runnable action) {
        doTransition(fromState, toState, () -> {
            action.run();
            return ExecutionResult.succeeded();
        }).getValueOrRethrow();
    }

    private <S> ExecutionResult<S> doTransition(T fromState, T toState, Supplier<ExecutionResult<S>> action) {
        CurrentState<T> current = state;
        current.assertCanTransition(fromState, toState);
        state = current.transitioningTo(toState);
        ExecutionResult<S> result;
        try {
            result = action.get();
        } catch (Throwable t) {
            result = ExecutionResult.failed(t);
        }
        if (!result.getFailures().isEmpty()) {
            state = state.failed(result);
        } else {
            state = state.nextState(toState);
        }
        return result;
    }

    private static abstract class CurrentState<T> {
        final DisplayName displayName;
        final T state;

        public CurrentState(DisplayName displayName, T state) {
            this.displayName = displayName;
            this.state = state;
        }

        public abstract void assertInState(T expected);

        public abstract void assertNotInState(T forbidden);

        public void assertCanTransition(T fromState, T toState) {
            assertCanTransition(fromState, toState, false);
        }

        public abstract void assertCanTransition(T fromState, T toState, boolean ignoreFailures);

        public abstract void assertCanTransition(List<T> fromStates, T toState, boolean ignoreFailures);

        public abstract boolean inStateAndNotTransitioning(T toState);

        public abstract boolean inStateOrTransitioningTo(T toState);

        public abstract boolean hasSeenStateAndNotTransitioning(T toState);

        public abstract boolean hasSeenStateIgnoringTransitions(T toState);

        public CurrentState<T> failed(ExecutionResult<?> failure) {
            return new Failed<>(displayName, state, failure);
        }

        public RuntimeException rethrow() {
            throw new IllegalStateException();
        }

        public ExecutionResult<Void> asResult() {
            return ExecutionResult.succeeded();
        }

        public CurrentState<T> transitioningTo(T toState) {
            return new TransitioningToNewState<T>(toState, this);
        }

        public abstract CurrentState<T> nextState(T toState);
    }

    /**
     * Currently in the given state.
     */
    private static class InState<T> extends CurrentState<T> {
        private final DisplayName displayName;
        @Nullable
        private final InState<T> previous;

        public InState(DisplayName displayName, T state, @Nullable InState<T> previous) {
            super(displayName, state);
            this.displayName = displayName;
            this.previous = previous;
        }

        @Override
        public void assertInState(T expected) {
            if (state != expected) {
                throw new IllegalStateException("Expected " + displayName.getDisplayName() + " to be in state " + expected + " but is in state " + state + ".");
            }
        }

        @Override
        public void assertNotInState(T forbidden) {
            if (state == forbidden) {
                throw new IllegalStateException(displayName.getCapitalizedDisplayName() + " should not be in state " + forbidden + ".");
            }
        }

        @Override
        public void assertCanTransition(T fromState, T toState, boolean ignoreFailures) {
            if (state != fromState) {
                throw new IllegalStateException("Can only transition " + displayName.getCapitalizedDisplayName() + " to state " + toState + " from state " + fromState + " however it is currently in state " + state + ".");
            }
        }

        @Override
        public void assertCanTransition(List<T> fromStates, T toState, boolean ignoreFailures) {
            if (!fromStates.contains(state)) {
                throw new IllegalStateException("Can only transition " + displayName.getCapitalizedDisplayName() + " to state " + toState + " from states " + fromStates + " however it is currently in state " + state + ".");
            }
        }

        @Override
        public boolean inStateAndNotTransitioning(T toState) {
            return state == toState;
        }

        @Override
        public boolean inStateOrTransitioningTo(T toState) {
            return inStateAndNotTransitioning(toState);
        }

        @Override
        public boolean hasSeenStateAndNotTransitioning(T toState) {
            if (state == toState) {
                return true;
            }
            if (previous != null) {
                return previous.hasSeenStateAndNotTransitioning(toState);
            }
            return false;
        }

        @Override
        public boolean hasSeenStateIgnoringTransitions(T toState) {
            return hasSeenStateAndNotTransitioning(toState);
        }

        @Override
        public CurrentState<T> nextState(T toState) {
            return new InState<>(displayName, toState, this);
        }
    }

    /**
     * Currently transitioning to a new state.
     */
    private static class TransitioningToNewState<T> extends CurrentState<T> {
        final T targetState;
        final CurrentState<T> fromState;

        public TransitioningToNewState(T targetState, CurrentState<T> fromState) {
            super(fromState.displayName, fromState.state);
            this.targetState = targetState;
            this.fromState = fromState;
        }

        @Override
        public boolean inStateAndNotTransitioning(T toState) {
            throw new IllegalStateException("Expected " + displayName.getDisplayName() + " to be in state " + toState + " but is in state " + state + " and transitioning to " + targetState + ".");
        }

        @Override
        public boolean inStateOrTransitioningTo(T toState) {
            if (targetState == toState) {
                return true;
            }
            throw new IllegalStateException("Expected " + displayName.getDisplayName() + " to be in state " + toState + " but is in state " + state + " and transitioning to " + targetState + ".");
        }

        @Override
        public void assertInState(T expected) {
            throw new IllegalStateException("Expected " + displayName.getDisplayName() + " to be in state " + expected + " but is in state " + state + " and transitioning to " + targetState + ".");
        }

        @Override
        public void assertNotInState(T forbidden) {
            throw new IllegalStateException(displayName.getCapitalizedDisplayName() + " should not be in state " + forbidden + " but is in state " + state + " and transitioning to " + targetState + ".");
        }

        @Override
        public void assertCanTransition(T fromState, T toState, boolean ignoreFailures) {
            failDueToTransition(toState);
        }

        @Override
        public void assertCanTransition(List<T> fromStates, T toState, boolean ignoreFailures) {
            failDueToTransition(toState);
        }

        private void failDueToTransition(T toState) {
            if (targetState == toState) {
                throw new IllegalStateException("Cannot transition " + displayName.getDisplayName() + " to state " + toState + " as already transitioning to this state.");
            } else {
                throw new IllegalStateException("Cannot transition " + displayName.getDisplayName() + " to state " + toState + " as already transitioning to state " + targetState + ".");
            }
        }

        @Override
        public boolean hasSeenStateAndNotTransitioning(T toState) {
            throw new IllegalStateException("Expected " + displayName.getDisplayName() + " to be in state " + toState + " or later but is in state " + state + " and transitioning to " + targetState + ".");
        }

        @Override
        public boolean hasSeenStateIgnoringTransitions(T toState) {
            return fromState.hasSeenStateIgnoringTransitions(toState);
        }

        @Override
        public CurrentState<T> nextState(T toState) {
            return fromState.nextState(toState);
        }
    }

    /**
     * A previous operation has failed.
     */
    private static class Failed<T> extends CurrentState<T> {
        final ExecutionResult<?> failure;

        public Failed(DisplayName displayName, T state, ExecutionResult<?> failure) {
            super(displayName, state);
            this.failure = failure;
        }

        public void throwFailure() {
            failure.rethrow();
        }

        @Override
        public void assertInState(T expected) {
            throwFailure();
        }

        @Override
        public void assertNotInState(T forbidden) {
            throwFailure();
        }

        @Override
        public void assertCanTransition(List<T> fromStates, T toState, boolean ignoreFailures) {
            if (!ignoreFailures) {
                throwFailure();
            }
        }

        @Override
        public void assertCanTransition(T fromState, T toState, boolean ignoreFailures) {
            if (!ignoreFailures) {
                throwFailure();
            }
        }

        @Override
        public boolean hasSeenStateAndNotTransitioning(T toState) {
            throwFailure();
            return false;
        }

        @Override
        public ExecutionResult<Void> asResult() {
            return failure.asFailure();
        }

        @Override
        public boolean inStateAndNotTransitioning(T toState) {
            throwFailure();
            return false;
        }

        @Override
        public boolean inStateOrTransitioningTo(T toState) {
            throwFailure();
            return false;
        }

        @Override
        public boolean hasSeenStateIgnoringTransitions(T toState) {
            throwFailure();
            return false;
        }

        @Override
        public RuntimeException rethrow() {
            failure.rethrow();
            throw new IllegalStateException();
        }

        @Override
        public CurrentState<T> failed(ExecutionResult<?> failure) {
            return new Failed<>(displayName, state, this.failure.withFailures(failure.asFailure()));
        }

        @Override
        public CurrentState<T> nextState(T toState) {
            return new Failed<>(displayName, toState, failure);
        }
    }

    public interface State {
    }
}
