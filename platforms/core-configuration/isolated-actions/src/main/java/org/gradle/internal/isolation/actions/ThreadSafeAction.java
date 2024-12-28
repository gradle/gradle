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

package org.gradle.internal.isolation.actions;

import org.gradle.api.Action;

/**
 * A user-provided action that is safe to call from any thread, without acquiring any locks.
 * <p>
 * Thread-safety is guaranteed either by:
 * <ul>
 *     <li>Using serialization and de-serialization to provide separate action instances independent from mutable state</li>
 *     <li>Guarding the original action with the appropriate lock</li>
 * </ul>
 */
public interface ThreadSafeAction<T> extends Action<T> {

}
