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
package org.gradle.internal.resolve;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.gradle.internal.resource.transport.http.HttpErrorStatusCodeException;

import java.io.InterruptedIOException;
import java.util.Collection;

public class ResolveExceptionAnalyzer {

    public static boolean hasCriticalFailure(Collection<? extends Throwable> failures) {
        for (Throwable failure : failures) {
            if (isCriticalFailure(failure)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCriticalFailure(Throwable throwable) {
        Throwable rootCause = ExceptionUtils.getRootCause(throwable);
        return isTimeoutException(rootCause) || isUnrecoverable5xxStatusCode(rootCause);
    }

    /**
     * See <a href="http://hc.apache.org/httpclient-3.x/exception-handling.html">HTTPClient exception handling</a> for more information.
     */
    private static boolean isTimeoutException(Throwable rootCause) {
        return rootCause instanceof InterruptedIOException;
    }

    private static boolean isUnrecoverable5xxStatusCode(Throwable rootCause) {
        return rootCause instanceof HttpErrorStatusCodeException && ((HttpErrorStatusCodeException) rootCause).isServerError();
    }
}
