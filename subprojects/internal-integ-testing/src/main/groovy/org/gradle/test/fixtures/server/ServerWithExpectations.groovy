/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.server

import org.junit.rules.ExternalResource
import org.slf4j.Logger

/**
 * This test fixture needs to be thread-safe for all operations that are done at execution time:
 *
 * - defining expectations is done serially in 'given', 'when' blocks, so doesn't need to be thread-safe
 * - but handlers, as well as failures, need to be thread-safe
 *
 */
abstract class ServerWithExpectations extends ExternalResource {

    protected Throwable failure

    void resetExpectations() {
        try {
            if (failure != null) {
                throw failure
            }
            for (ServerExpectation e in expectations) {
                e.assertMet()
            }
        } finally {
            failure = null
            expectations.clear()
        }
    }

    @Override
    protected void after() {
        stop()
        resetExpectations()
    }

    protected synchronized void onFailure(Throwable failure) {
        logger.error(failure.message)
        if (this.failure == null) {
            this.failure = failure
        }
    }

    abstract protected List<? extends ServerExpectation> getExpectations()
    abstract protected void stop()
    abstract protected Logger getLogger()
}
