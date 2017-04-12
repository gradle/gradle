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

import groovy.transform.CompileStatic
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.spockframework.runtime.UnallowedExceptionThrownError
import org.spockframework.runtime.WrongExceptionThrownError
import org.spockframework.runtime.model.MethodInfo
import org.spockframework.runtime.model.SpecInfo
import spock.lang.Specification

/*
  Use this rule judiciously. This is not intended as a generic solution for flaky tests.
  There are few valid use cases for it like known issues in old versions of Gradle impacting cross-version tests.

  Implements MethodRule, not TestRule because of a limitation of Spock.
  Note that setup or cleanup methods won't be called between retrying and the same test instance is used for retrying.
  If the test method depends on state held in the test instance, then retrying might not behave as expected.
  See this thread for more details: https://groups.google.com/forum/#!msg/spockframework/95ACCVg-aCQ/0SIvxoLhX7UJ:
 */
@CompileStatic
class RetryRule implements MethodRule {

    private Closure<Boolean> shouldRetry
    private Specification specification

    protected RetryRule(Specification specification, Closure<Boolean> shouldRetry) {
        this.specification = specification
        this.shouldRetry = shouldRetry
    }

    static RetryRule retryIf(Specification specification, Closure<Boolean> shouldRetry) {
        return new RetryRule(specification, shouldRetry)
    }

    static RetryRule retryIf(Closure<Boolean> shouldRetry) {
        return new RetryRule(null, shouldRetry)
    }

    @Override
    Statement apply(Statement base, FrameworkMethod method, Object target) {
        if (specification == null) {
            specification = target as Specification
        }
        return {
            try {
                base.evaluate()
            } catch (Throwable t1) {
                if (shouldReallyRetry(t1)) {
                    try {
                        println "Retrying (2nd attempt) " + method
                        runCleanup(specification?.specificationContext?.currentSpec)
                        reRunSetup(specification?.specificationContext?.currentSpec)
                        base.evaluate()
                    } catch (Throwable t2) {
                        if (shouldReallyRetry(t2)) {
                            try {
                                println "Retrying (3rd attempt) " + method
                                runCleanup(specification?.specificationContext?.currentSpec)
                                reRunSetup(specification?.specificationContext?.currentSpec)
                                base.evaluate()
                            } catch (Throwable t3) {
                                throw new RetryFailure(t3)
                            }
                        } else {
                            throw t2
                        }
                    }
                } else {
                    throw t1
                }
            }
        } as Statement
    }

    private void runCleanup(SpecInfo spec) {
        if (spec != null) {
            runCleanup(spec.getSuperSpec())
            for (MethodInfo method : spec.getCleanupMethods()) {
                method.invoke(specification)
            }
        }
    }

    private void reRunSetup(SpecInfo spec) {
        if (spec != null) {
            reRunSetup(spec.getSuperSpec())
            for (MethodInfo method : spec.getSetupMethods()) {
                method.invoke(specification)
            }
        }
    }

    private boolean shouldReallyRetry(Throwable t) {
        if (t instanceof WrongExceptionThrownError && t.getCause() != null) {
            return shouldRetry(t.getCause())
        }
        if (t instanceof UnallowedExceptionThrownError && t.getCause() != null) {
            return shouldRetry(t.getCause())
        }
        shouldRetry(t)
    }
}
