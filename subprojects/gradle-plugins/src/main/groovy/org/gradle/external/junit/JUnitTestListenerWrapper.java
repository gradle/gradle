/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.external.junit;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestListener;

import java.lang.reflect.Method;

/**
 * Based on Ant's wrapListener so we keep this behavior the same.
 *
 * @author Tom Eyckmans
 */
public class JUnitTestListenerWrapper implements junit.framework.TestListener {

    private final junit.framework.TestListener wrappedListener;

    public JUnitTestListenerWrapper(final TestListener wrappedListener) {
        this.wrappedListener = wrappedListener;
    }

    public void addError(Test test, Throwable t) {
        if (JUnit4Detecter.isJUnit4Available() && t instanceof AssertionFailedError) {
            // JUnit 4 does not distinguish between errors and failures
            // even in the JUnit 3 adapter.
            // So we need to help it a bit to retain compatibility for JUnit 3 tests.
            wrappedListener.addFailure(test, (AssertionFailedError) t);
        } else if (JUnit4Detecter.isJUnit4Available() && t.getClass().getName().equals("java.lang.AssertionError")) {
            // Not strictly necessary but probably desirable.
            // JUnit 4-specific test GUIs will show just "failures".
            // But Ant's output shows "failures" vs. "errors".
            // We would prefer to show "failure" for things that logically are.
            try {
                String msg = t.getMessage();
                AssertionFailedError failure = msg != null
                        ? new AssertionFailedError(msg) : new AssertionFailedError();
                // To compile on pre-JDK 4 (even though this should always succeed):
                Method initCause = Throwable.class.getMethod(
                        "initCause", new Class[]{Throwable.class});
                initCause.invoke(failure, new Object[]{t});
                wrappedListener.addFailure(test, failure);
            } catch (Exception e) {
                // Rats.
                e.printStackTrace(); // should not happen
                wrappedListener.addError(test, t);
            }
        } else {
            wrappedListener.addError(test, t);
        }
    }

    public void addFailure(Test test, AssertionFailedError t) {
        wrappedListener.addFailure(test, t);
    }

    public void addFailure(Test test, Throwable t) { // pre-3.4
        if (t instanceof AssertionFailedError) {
            wrappedListener.addFailure(test, (AssertionFailedError) t);
        } else {
            wrappedListener.addError(test, t);
        }
    }

    public void endTest(Test test) {
        wrappedListener.endTest(test);
    }

    public void startTest(Test test) {
        wrappedListener.startTest(test);
    }
}
