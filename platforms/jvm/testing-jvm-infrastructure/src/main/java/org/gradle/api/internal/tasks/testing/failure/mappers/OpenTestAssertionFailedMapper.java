/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.failure.mappers;

import org.gradle.api.internal.tasks.testing.failure.TestFailureMapper;
import org.gradle.api.internal.tasks.testing.failure.ThrowableToTestFailureMapper;
import org.gradle.api.tasks.testing.TestFailure;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;


/**
 * Maps {@code org.opentest4j.AssertionFailedError} to {@link TestFailure}.
 * <p>
 * This mapper is a bit more complex, as depending on the type of the expected and actual values,
 * the assertion will be mapped as a string-based comparison failure or a file comparison failure.
 * <p>
 * See {@link TestFailureMapper} for more details about failure mapping.
 */
public class OpenTestAssertionFailedMapper extends TestFailureMapper {

    @Override
    protected List<String> getSupportedClassNames() {
        return Collections.singletonList("org.opentest4j.AssertionFailedError");
    }

    @Override
    public TestFailure map(Throwable throwable, ThrowableToTestFailureMapper rootMapper) throws Exception {
        // Unwrap the expected and actual values from their ValueWrapper instances
        Object expectedValue = invokeMethod(
            invokeMethod(throwable, "getExpected"),
            "getValue"
        );
        Object actualValue = invokeMethod(
            invokeMethod(throwable, "getActual"),
            "getValue"
        );

        if (isFileInfo(expectedValue) || isFileInfo(actualValue)) {
            return mapFileInfoComparisonFailure(throwable, expectedValue, actualValue);
        } else {
            return mapStringBasedComparisonFailure(throwable, expectedValue, actualValue);
        }
    }

    private static TestFailure mapFileInfoComparisonFailure(Throwable throwable, @Nullable Object expected, @Nullable Object actual) throws Exception {
        String expectedPath = getFilePathOrString(expected);
        byte[] expectedContent = getContentsOrNull(expected);
        String actualPath = getFilePathOrString(actual);
        byte[] actualContent = getContentsOrNull(actual);
        return TestFailure.fromFileComparisonFailure(throwable, expectedPath, actualPath, expectedContent, actualContent, null);
    }

    private static TestFailure mapStringBasedComparisonFailure(Throwable throwable, @Nullable Object expected, @Nullable Object actual) throws Exception {
        // We call here the handy getFilePathOrString method which optionally could handle FileInfo,
        // but in this case it will just return the string representation of the value
        String actualString = getFilePathOrString(actual);
        String expectedString = getFilePathOrString(expected);

        return TestFailure.fromTestAssertionFailure(throwable, expectedString, actualString);
    }

    private static boolean isFileInfo(@Nullable Object value) {
        return value != null && isAssignableFrom("org.opentest4j.FileInfo", value.getClass());
    }

    private static boolean isAssignableFrom(String className, @Nullable Class<?> aClass) {
        if (aClass == null) {
            return false;
        }
        if (className.equals(aClass.getName())) {
            return true;
        }
        return isAssignableFrom(className, aClass.getSuperclass());
    }

    /**
     * Returns different representations based on the {@code value} type:
     * <p>
     * This is also a handy method for just getting a safe string representation of a value.
     */
    @Nullable
    private static String getFilePathOrString(@Nullable Object value) throws Exception {
        if (value == null) {
            return null;
        } else if (isFileInfo(value)) {
            return invokeMethod(value, "getPath", String.class);
        } else {
            return value.toString();
        }
    }

    /**
     * Returns the contents of a {@code FileInfo} or null if the value is not a {@code FileInfo}.
     */
    @Nullable
    private static byte[] getContentsOrNull(@Nullable Object value) throws Exception {
        if (isFileInfo(value)) {
            return invokeMethod(value, "getContents", byte[].class);
        } else {
            return null;
        }
    }

}
