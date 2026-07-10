/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.concurrent;

/**
 * A {@link Stoppable} object whose stop process can be performed asynchronously.
 */
public interface AsyncStoppable extends Stoppable {
    /**
     * <p>Requests that this stoppable commence a graceful stop. Does not block. You should call {@link
     * Stoppable#stop} to wait for the stop process to complete.</p>
     *
     * <p>Generally, an {@code AsyncStoppable} should continue to complete existing work after this method has returned.
     * It should, however, stop accepting new work.</p>
     *
     * <p>
     * Requesting stopping does not guarantee the stoppable actually stops.
     * Requesting stopping means preparing for stopping; stopping accepting new work.
     * You have to call stop at some point anyway if your intention is to completely stop the stoppable.
     */
    void requestStop();
}
