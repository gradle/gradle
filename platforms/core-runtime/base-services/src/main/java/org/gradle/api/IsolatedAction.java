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
 * Defines an action that is applied to objects of type {@link T}, with each target getting its own isolated action instance.
 *
 * Each isolated action is re-created through Configuration Cache serialization before it is applied to a target.
 * This approach ensures that sharing mutable state across targets via any means
 * (including {@link org.gradle.api.services.BuildService}, which are not supported) is prevented.
 * The absence of shared mutable state allows these actions to be safely executed in parallel as needed.
 *
 * <p><b>IMPORTANT:</b> As isolated action instances are recreated using Configuration Cache serialization, they must
 * adhere to the <a href="https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:requirements">same requirements</a>
 * as any other object serialized into the Configuration Cache.</p>
 *
 * @param <T> The type of object the action operates on.
 * @since 8.8
 */
@HasImplicitReceiver
@Incubating
public interface IsolatedAction<T> extends Serializable {
    /**
     * Performs this action against the given object.
     *
     * @param target The object to perform the action on.
     * @since 8.8
     */
    void execute(T target);
}
