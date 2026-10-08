/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api

import org.junit.Test

import static org.junit.Assert.assertEquals

class AllGradleExceptionsTest {
    private static final List EXCEPTION_CLASSES = [UnknownTaskException, UnknownProjectException, InvalidUserDataException, GradleException, CircularReferenceException]

    @Test
    void testWithMessage() {
        String expectedMessage = 'somemessage'
        checkException([expectedMessage]) { GradleException exception ->
            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    void testWithMessageAndCause() {
        String expectedMessage = 'somemessage'
        Throwable expectedCause = new Throwable()
        checkException([expectedMessage, expectedCause]) { GradleException exception ->
            assertEquals(expectedMessage, exception.message)
            assertEquals(expectedCause, exception.cause)
        }
    }

    void checkException(List constructorArgs, Closure testClosure) {
        EXCEPTION_CLASSES.each { Class clazz ->
            GradleException exception = clazz.newInstance(constructorArgs as Object[]) as GradleException
            testClosure(exception)
        }
    }
}
