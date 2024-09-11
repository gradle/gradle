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
package org.gradle.internal.exceptions;

import org.gradle.internal.Factory;

/**
 * A specialized version of multi cause exception that is cheaper to create
 * because we avoid to fill a stack trace, and the message MUST be generated lazily.
 */
@Contextual
public class DefaultMultiCauseExceptionNoStackTrace extends DefaultMultiCauseException {
    public DefaultMultiCauseExceptionNoStackTrace(Factory<String> messageFactory) {
        super(messageFactory);
    }

    public DefaultMultiCauseExceptionNoStackTrace(Factory<String> messageFactory, Throwable... causes) {
        super(messageFactory, causes);
    }

    public DefaultMultiCauseExceptionNoStackTrace(Factory<String> messageFactory, Iterable<? extends Throwable> causes) {
        super(messageFactory, causes);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
