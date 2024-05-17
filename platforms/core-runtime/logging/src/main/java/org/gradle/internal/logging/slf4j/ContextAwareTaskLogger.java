/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.logging.slf4j;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.internal.operations.OperationIdentifier;

import javax.annotation.Nullable;

public interface ContextAwareTaskLogger extends Logger {
    interface MessageRewriter {

        /**
         * Rewrites log message.
         *
         * @param logLevel the logging level
         * @param message the original message
         * @return the rewritten message or null if this message should be silenced
         */
        @Nullable
        String rewrite(LogLevel logLevel, String message);
    }

    void setMessageRewriter(MessageRewriter messageRewriter);

    void setFallbackBuildOperationId(OperationIdentifier operationIdentifier);
}
