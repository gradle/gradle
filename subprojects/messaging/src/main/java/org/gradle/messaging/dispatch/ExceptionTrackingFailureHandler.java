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

package org.gradle.messaging.dispatch;

import org.gradle.internal.concurrent.Stoppable;
import org.slf4j.Logger;

public class ExceptionTrackingFailureHandler implements DispatchFailureHandler<Object>, Stoppable {
    private final Logger logger;
    private DispatchException failure;

    public ExceptionTrackingFailureHandler(Logger logger) {
        this.logger = logger;
    }

    public void dispatchFailed(Object message, Throwable failure) {
        if (this.failure != null) {
            logger.error(failure.getMessage(), failure);
        } else {
            this.failure = new DispatchException(String.format("Could not dispatch message %s.", message), failure);
        }
    }

    public void stop() throws DispatchException {
        if (failure != null) {
            try {
                throw failure;
            } finally {
                failure = null;
            }
        }
    }
}
