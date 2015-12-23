/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testing.internal.util

import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

/*
  Implements MethodRule, not TestRule because of a limitation of Spock.
  Note that setup or cleanup methods won't be called between retrying and the same test instance is used for retrying.
  If the test method depends on state held in the test instance, then retrying might not behave as expected.
  See this thread for more details: https://groups.google.com/forum/#!msg/spockframework/95ACCVg-aCQ/0SIvxoLhX7UJ:
 */
class RetryRule implements MethodRule {

    private Closure<Boolean> shouldRetry;

    private RetryRule(Closure<Boolean> shouldRetry) {
        this.shouldRetry = shouldRetry
    }

    static RetryRule retryIf(Closure<Boolean> shouldRetry) {
        return new RetryRule(shouldRetry)
    }

    @Override
    Statement apply(Statement base, FrameworkMethod method, Object target) {
        return {
            try {
                base.evaluate()
            } catch (Throwable t1) {
                if (shouldRetry(t1)) {
                    try {
                        base.evaluate()
                    } catch (Throwable t2) {
                        if (shouldRetry(t2)) {
                            try {
                                base.evaluate();
                            } catch (Throwable t3) {
                                throw new RetryFailure(t3);
                            }
                        } else {
                            throw t2;
                        }
                    }
                } else {
                    throw t1;
                }
            }
        }
    }
}
