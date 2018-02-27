/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.protocol;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * A result of one of the actions of an {@link InternalPhasedAction}.
 * This result will be supplied to {@link PhasedActionResultListener}.
 *
 * @since 4.7
 */
public interface PhasedActionResult<T> extends InternalProtocolInterface, Serializable {

    /**
     * Gets the action result if it completed successfully.
     *
     * @return The result. {@code null} if a failure occurred.
     *
     * @since 4.7
     */
    @Nullable
    T getResult();

    /**
     * Gets the action failure if it did not completed successfully.
     *
     * @return The failure. {@code null} if action completed successfully.
     *
     * @since 4.7
     */
    @Nullable
    Throwable getFailure();
}
