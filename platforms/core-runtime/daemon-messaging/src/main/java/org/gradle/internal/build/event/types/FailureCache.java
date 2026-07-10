/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalFailure;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Deduplicates failure conversion by the identity of the original {@link Throwable}.
 * <p>
 * A failed Isolated Projects sync attaches the same configuration failure to every per-project fetch, so the same
 * {@code Throwable} (the whole tree or a shared deep cause) is converted many times. Keying by {@code Throwable}
 * identity lets those conversions reuse a single {@link InternalFailure}.
 */
@NullMarked
public interface FailureCache {

    /**
     * Returns the failure already converted from {@code original}, or {@code null} if none has been converted yet.
     */
    @Nullable
    InternalFailure get(Throwable original);

    /**
     * Records {@code converted} as the conversion of {@code original} unless one already exists, and returns the
     * canonical instance for that throwable (the pre-existing one if another conversion won the race).
     */
    InternalFailure intern(Throwable original, InternalFailure converted);

    /**
     * A cache that stores nothing; every conversion stays independent. Used when conversion cannot benefit from reuse.
     */
    FailureCache NONE = new FailureCache() {
        @Override
        @Nullable
        public InternalFailure get(Throwable original) {
            return null;
        }

        @Override
        public InternalFailure intern(Throwable original, InternalFailure converted) {
            return converted;
        }
    };
}
