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

package org.gradle.api;

import java.io.Serializable;

/**
 * Performs some action against objects of type {@link T}.
 *
 * <p>
 * <b>IMPORTANT: </b> Isolated action instances are never reused. TBD
 * </p>
 *
 * @param <T> The type of object which the action expects.
 * @since 8.7
 */
@HasImplicitReceiver
@Incubating
@FunctionalInterface
public interface IsolatedAction<T> extends Serializable {
    /**
     * Performs this action against the given object.
     *
     * @param target The object to perform the action on.
     * @since 8.7
     */
    void execute(T target);
}
