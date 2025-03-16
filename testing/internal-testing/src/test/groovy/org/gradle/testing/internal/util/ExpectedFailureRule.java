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

package org.gradle.testing.internal.util;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

public class ExpectedFailureRule implements MethodRule {

    @Override
    public Statement apply(final Statement base, final FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                ExpectedFailure expectedFailureAnnotation = method.getAnnotation(ExpectedFailure.class);
                boolean expectedToFail = expectedFailureAnnotation != null;
                Throwable failed = null;
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    failed = t;
                    if (!expectedToFail) {
                        throw t;
                    }
                }
                if (expectedToFail) {
                    if (failed == null) {
                        throw new AssertionError("test was expected to fail but didn't");
                    }
                    if (!failed.getClass().equals(expectedFailureAnnotation.expected())) {
                        throw new AssertionError("test was expected but with " + expectedFailureAnnotation.expected() + " but failed with " + failed.getClass());
                    }
                }
            }
        };
    }
}
