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

import org.gradle.api.Action;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.specs.Spec;
import org.gradle.internal.time.Clock;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@NullMarked
public class DefaultUserCodeApplicationContext implements UserCodeApplicationContext {

    private static final AtomicLong COUNTER = new AtomicLong();

    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<CurrentTiming> currentTiming = new ThreadLocal<CurrentTiming>() {
        @Override
        protected CurrentTiming initialValue() {
            return new CurrentTiming();
        }
    };

    private final Clock clock;
    private final UserCodeApplicationRegistry applicationRegistry;

    public DefaultUserCodeApplicationContext(
        Clock clock,
        UserCodeApplicationRegistry applicationRegistry
    ) {
        this.clock = clock;
        this.applicationRegistry = applicationRegistry;
    }

    @Override
    public @Nullable Application current() {
        return currentTiming.get().application;
    }

    @Override
    public void apply(
        @Nullable UserCodeSource source,
        @Nullable Path projectIdentityPath,
        Action<? super UserCodeApplicationId> action
    ) {
        UserCodeApplicationId id = newId();
        DefaultApplication newApplication = new DefaultApplication(id, source);
        if (projectIdentityPath != null) {
            applicationRegistry.register(projectIdentityPath, newApplication);
        }
        newApplication.reapplyAction(action, id, CodeType.GENERAL);
    }

    @Override
    public <T> Action<T> reapplyActionLaterForCurrent(Action<T> action, CodeType codeType) {
        DefaultApplication application = currentTiming.get().application;
        if (application == null) {
            return action;
        }
        return SerializableLambdas.action(param -> application.reapplyAction(action, param, codeType));
    }

    @Override
    public <T> Spec<T> reapplySpecLaterForCurrent(Spec<T> spec, CodeType codeType) {
        DefaultApplication application = currentTiming.get().application;
        if (application == null) {
            return spec;
        }
        return SerializableLambdas.spec(param -> application.reapplySpec(spec, param, codeType));
    }

    @Override
    public void gradleRuntime(Runnable runnable) {
        CurrentTiming timing = currentTiming.get();
        DefaultApplication savedApp = timing.application;
        CodeType savedType = timing.codeType;
        pushTiming(timing, null, null);
        try {
            runnable.run();
        } finally {
            popTiming(timing, savedApp, savedType);
        }
    }

    private static UserCodeApplicationId newId() {
        return new UserCodeApplicationId(COUNTER.incrementAndGet());
    }

    private class DefaultApplication implements Application {

        private final UserCodeApplicationId id;
        private final @Nullable UserCodeSource source;

        private final AtomicLong generalDurationNs = new AtomicLong(0);
        private final AtomicLong callbackDurationNs = new AtomicLong(0);
        private final AtomicLong listenerDurationNs = new AtomicLong(0);

        public DefaultApplication(UserCodeApplicationId id, @Nullable UserCodeSource source) {
            this.id = id;
            this.source = source;
        }

        @Override
        public UserCodeApplicationId getId() {
            return id;
        }

        @Override
        public @Nullable UserCodeSource getSource() {
            return source;
        }

        @Override
        public void reapply(Runnable runnable, CodeType codeType) {
            CurrentTiming timing = currentTiming.get();
            DefaultApplication savedApp = timing.application;
            CodeType savedType = timing.codeType;
            pushTiming(timing, this, codeType);
            try {
                runnable.run();
            } finally {
                popTiming(timing, savedApp, savedType);
            }
        }

        @Override
        public <T> T reapply(Supplier<T> action, CodeType codeType) {
            CurrentTiming timing = currentTiming.get();
            DefaultApplication savedApp = timing.application;
            CodeType savedType = timing.codeType;
            pushTiming(timing, this, codeType);
            try {
                return action.get();
            } finally {
                popTiming(timing, savedApp, savedType);
            }
        }

        @Override
        public <T> void reapplyAction(Action<T> action, T param, CodeType codeType) {
            CurrentTiming timing = currentTiming.get();
            DefaultApplication savedApp = timing.application;
            CodeType savedType = timing.codeType;
            pushTiming(timing, this, codeType);
            try {
                action.execute(param);
            } finally {
                popTiming(timing, savedApp, savedType);
            }
        }

        @Override
        public <T> boolean reapplySpec(Spec<T> spec, T param, CodeType codeType) {
            CurrentTiming timing = currentTiming.get();
            DefaultApplication savedApp = timing.application;
            CodeType savedType = timing.codeType;
            pushTiming(timing, this, codeType);
            try {
                return spec.isSatisfiedBy(param);
            } finally {
                popTiming(timing, savedApp, savedType);
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

        private void accumulateTime(long durationNs, CodeType codeType) {
            switch (codeType) {
                case GENERAL: generalDurationNs.addAndGet(durationNs); break;
                case COLLECTION_CALLBACK: callbackDurationNs.addAndGet(durationNs); break;
                case LISTENER: listenerDurationNs.addAndGet(durationNs); break;
                default: throw new IllegalArgumentException("Unknown code type: " + codeType);
            }
        }

    }

    private void pushTiming(CurrentTiming timing, @Nullable DefaultApplication accumulator, @Nullable CodeType type) {
        long now = clock.nanoTime();
        if (timing.application != null) {
            timing.application.accumulateTime(now - timing.startNanos, timing.codeType);
        }
        timing.application = accumulator;
        timing.codeType = type;
        timing.startNanos = now;
    }

    private void popTiming(CurrentTiming timing, @Nullable DefaultApplication savedApp, @Nullable CodeType savedType) {
        long now = clock.nanoTime();
        if (timing.application != null) {
            timing.application.accumulateTime(now - timing.startNanos, timing.codeType);
        }
        timing.application = savedApp;
        timing.codeType = savedType;
        timing.startNanos = now;
    }

    private static class CurrentTiming {
        DefaultUserCodeApplicationContext.@Nullable DefaultApplication application;
        UserCodeApplicationContext.@Nullable CodeType codeType;
        long startNanos;
    }

}
