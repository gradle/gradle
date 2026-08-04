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

package org.gradle.internal.code;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class DefaultUserCodeApplicationContext implements UserCodeApplicationContext {

    /**
     * Tracks the current application in progress for the current thread. Null when the current
     * thread has never executed user code for this context.
     * <p>
     * This is deliberately not static. Each context tracks its own applications independently,
     * so that user code running in one context is transparent to the application timings of
     * another. In production, this scenario is not expected to occur, but this is likely more
     * correct in testing scenarios involving multiple {@code ProjectBuilder} instances.
     * <p>
     * This intentioanlly does not use {@link ThreadLocal#withInitial}. Threads that only ever
     * query {@link #current()} must not pay the cost of materializing a thread local map entry.
     * The supplier indirection behind {@code withInitial} is a megamorphic call site shared by
     * every such thread local in the JVM. State is created lazily by {@link #threadState()} only
     * on threads that actually execute user code.
     */
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<@Nullable ThreadState> currentTiming = new ThreadLocal<>();

    private final NanoTimeProvider timeProvider;

    /**
     * The recording in progress, or null if no recording is in progress.
     */
    private volatile @Nullable RecordingState recording;

    public DefaultUserCodeApplicationContext(NanoTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    @Override
    public Recording startRecording() {
        if (this.recording != null) {
            throw new IllegalStateException("Cannot record multiple user code application timings simultaneously");
        }

        RecordingState recording = new RecordingState();
        this.recording = recording;
        return recording;
    }

    @Override
    public void apply(
        UserCodeSource source,
        Target target,
        Action<? super UserCodeApplicationId> action
    ) {
        RecordingState recording = this.recording;
        if (recording == null) {
            throw new IllegalStateException("Cannot apply user code application outside of a user code application timing");
        }

        UserCodeApplicationId id = new UserCodeApplicationId(recording.nextId());
        DefaultApplication newApplication = new DefaultApplication(id, source);
        recording.registerApplication(target, newApplication);
        newApplication.reapplyAction(action, id, CodeType.GENERAL);
    }

    @Override
    public @Nullable Application current() {
        ThreadState timing = currentTiming.get();
        return timing == null ? null : timing.application;
    }

    /**
     * Get the current thread's timing state, creating it if this thread has not executed
     * user code for this context before.
     */
    private ThreadState threadState() {
        ThreadState timing = currentTiming.get();
        if (timing == null) {
            timing = new ThreadState();
            currentTiming.set(timing);
        }
        return timing;
    }

    @Override
    public void gradleRuntime(Runnable runnable) {
        ThreadState timing = currentTiming.get();
        if (timing == null || timing.application == null) {
            // No user code is executing on this thread, so there is no timer to pause.
            runnable.run();
            return;
        }
        DefaultApplication savedApp = timing.application;
        CodeType savedType = timing.codeType;
        swapTimer(timing, null, null);
        try {
            runnable.run();
        } finally {
            swapTimer(timing, savedApp, savedType);
        }
    }

    @Override
    public ImmutableList<Application> getApplicationsFor(Target target) {
        RecordingState recording = this.recording;
        if (recording == null) {
            throw new IllegalStateException("Cannot get user code applications while recording is not in progress.");
        }
        return recording.getApplicationsFor(target);
    }

    /**
     * A source of time values, used to stub out the time source in tests.
     * <p>
     * We use a custom interface rather than {@link java.util.function.LongSupplier} so that there is
     * only a single implementation of this interface at runtime, allowing the JVM to inline the method.
     */
    public interface NanoTimeProvider {

        long nanoTime();

    }

    private class DefaultApplication implements Application {

        private final UserCodeApplicationId id;
        private final UserCodeSource source;

        private final AtomicLong generalDurationNs = new AtomicLong(0);
        private final AtomicLong callbackDurationNs = new AtomicLong(0);
        private final AtomicLong listenerDurationNs = new AtomicLong(0);

        public DefaultApplication(UserCodeApplicationId id, UserCodeSource source) {
            this.id = id;
            this.source = source;
        }

        @Override
        public UserCodeApplicationId getId() {
            return id;
        }

        @Override
        public UserCodeSource getSource() {
            return source;
        }

        @Override
        public void reapply(Runnable runnable, CodeType codeType) {
            ThreadState timing = threadState();
            DefaultApplication savedApp = timing.application;
            CodeType savedType = timing.codeType;
            swapTimer(timing, this, codeType);
            try {
                runnable.run();
            } finally {
                swapTimer(timing, savedApp, savedType);
            }
        }

        @Override
        public <T> T reapplySupplier(Supplier<T> action, CodeType codeType) {
            ThreadState timing = threadState();
            DefaultApplication savedApp = timing.application;
            CodeType savedType = timing.codeType;
            swapTimer(timing, this, codeType);
            try {
                return action.get();
            } finally {
                swapTimer(timing, savedApp, savedType);
            }
        }

        @Override
        public <T> void reapplyAction(Action<T> action, T param, CodeType codeType) {
            ThreadState timing = threadState();
            DefaultApplication savedApp = timing.application;
            CodeType savedType = timing.codeType;
            swapTimer(timing, this, codeType);
            try {
                action.execute(param);
            } finally {
                swapTimer(timing, savedApp, savedType);
            }
        }

        @Override
        public <T> boolean reapplySpec(Spec<T> spec, T param, CodeType codeType) {
            ThreadState timing = threadState();
            DefaultApplication savedApp = timing.application;
            CodeType savedType = timing.codeType;
            swapTimer(timing, this, codeType);
            try {
                return spec.isSatisfiedBy(param);
            } finally {
                swapTimer(timing, savedApp, savedType);
            }
        }

        @Override
        public long getTotalDurationNs() {
            return generalDurationNs.get() + callbackDurationNs.get() + listenerDurationNs.get();
        }

        @Override
        public long getDurationNsForType(CodeType codeType) {
            switch (codeType) {
                case GENERAL: return generalDurationNs.get();
                case COLLECTION_CALLBACK: return callbackDurationNs.get();
                case LISTENER: return listenerDurationNs.get();
                default: throw new IllegalArgumentException("Unknown code type: " + codeType);
            }
        }

        /**
         * Add the given duration to the timer for the given code type.
         */
        private void accumulateTime(long durationNs, CodeType codeType) {
            switch (codeType) {
                case GENERAL: generalDurationNs.addAndGet(durationNs); break;
                case COLLECTION_CALLBACK: callbackDurationNs.addAndGet(durationNs); break;
                case LISTENER: listenerDurationNs.addAndGet(durationNs); break;
                default: throw new IllegalArgumentException("Unknown code type: " + codeType);
            }
        }

    }

    /**
     * Update the given {@link ThreadState} to begin tracking code of the given type in the given application.
     * If the current thread has an existing application, accumulate the time spent since that application
     * was first applied into that application's timer.
     * <p>
     * This method and its callers are specifically designed to be performant and avoid allocating new objects.
     * This code path is executed very frequently during a build and should be as lightweight as possible. Callers
     * should store to-be-restored state on the stack and pass them individually as parameters to this method
     * rather than consolidating them into a single object.
     *
     * @param timing The current thread's timing state.
     * @param newApplication The application being transitioned to, or null if transitioning to no user code application.
     * @param newType The type of code being transitioned to, or null if transitioning to no user code application.
     */
    private void swapTimer(ThreadState timing, @Nullable DefaultApplication newApplication, @Nullable CodeType newType) {
        long now = timeProvider.nanoTime();
        if (timing.application != null) {
            timing.application.accumulateTime(now - timing.startNanos, Objects.requireNonNull(timing.codeType));
        }
        timing.application = newApplication;
        timing.codeType = newType;
        timing.startNanos = now;
    }

    /**
     * Tracks current user code application state for a given thread.
     */
    private static class ThreadState {

        /**
         * The current application being executed, or null if no use code application is currently being executed.
         */
        @Nullable DefaultApplication application;

        /**
         * The type of user code being executed, or null if no user code application is currently being executed.
         */
        @Nullable CodeType codeType;

        /**
         * The time at which the current application was started, or undefined if no user code application is currently being executed.
         */
        long startNanos;

    }

    /**
     * Tracks all user code applications that have been applied while a recording is in progress.
     */
    private class RecordingState implements Recording {

        /**
         * Monotonic counter for generating unique application IDs.
         */
        private final AtomicLong counter = new AtomicLong();

        @Override
        public ImmutableMap<Target, ImmutableList<Application>> stop() {
            if (DefaultUserCodeApplicationContext.this.recording != this) {
                throw new IllegalStateException("This recording is not the recording in progress");
            }
            DefaultUserCodeApplicationContext.this.recording = null;
            return getAllApplications();
        }

        /**
         * All known user code applications, mapped by the identity path of the project that they were applied to.
         */
        private final ConcurrentHashMap<Target, List<Application>> applications = new ConcurrentHashMap<>();

        /**
         * Return an ID, unique to this recording, to identify a new user code application.
         */
        public long nextId() {
            return counter.incrementAndGet();
        }

        /**
         * Register a new user code application applied to the given target.
         */
        public void registerApplication(Target target, Application application) {
            // Applications are generally only applied to a given target from a single thread.
            // Application timings are generally only read after the application has been applied.
            // We use a synchronized list rather than a CopyOnWriteArrayList to avoid the overhead
            // of copying the list upon registration, as we do not expect concurrent access to the
            // list to be common.
            applications.computeIfAbsent(target, k -> Collections.synchronizedList(new ArrayList<>())).add(application);
        }

        /**
         * Return all applications applied to the given target.
         * <p>
         * This method is thread-safe, but not atomic. This method may be called concurrently while
         * user code applications are being registered or executed. However, applications registered
         * while this method is executing may or may not be included in the returned list.
         */
        public ImmutableList<Application> getApplicationsFor(Target target) {
            return ImmutableList.copyOf(applications.getOrDefault(target, Collections.emptyList()));
        }

        /**
         * Return all applications applied to all targets.
         * <p>
         * This method is thread-safe, but not atomic. This method may be called concurrently while
         * user code applications are being registered or executed. However, applications registered
         * while this method is executing may or may not be included in the returned map.
         */
        public ImmutableMap<Target, ImmutableList<Application>> getAllApplications() {
            ImmutableMap.Builder<Target, ImmutableList<Application>> result = ImmutableMap.builderWithExpectedSize(this.applications.size());
            for (Map.Entry<Target, List<Application>> entry : this.applications.entrySet()) {
                result.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
            }
            return result.build();
        }

    }

}
