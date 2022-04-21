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

package org.gradle.api.internal.artifacts.repositories.transport;

import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.resource.transport.http.HttpErrorStatusCodeException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;

public class NetworkingIssueVerifier {

    // Too many requests (not available through HttpStatus.XXX)
    private static final int SC_TOO_MANY_REQUESTS = 429;

    /**
     * Determines if an error should cause a retry. We will currently retry:
     * <ul>
     * <li>on a network timeout</li>
     * <li>on a server error (return code 5xx)</li>
     * <li>on rate limiting</li>
     * </ul>
     */
    public static <E extends Throwable> boolean isLikelyTransientNetworkingIssue(E failure) {
        if (failure instanceof SocketException || failure instanceof SocketTimeoutException || failure instanceof NoHttpResponseException) {
            return true;
        }
        if (failure instanceof DefaultMultiCauseException) {
            List<? extends Throwable> causes = ((DefaultMultiCauseException) failure).getCauses();
            for (Throwable cause : causes) {
                if (isLikelyTransientNetworkingIssue(cause)) {
                    return true;
                }
            }
        }
        if (failure instanceof HttpErrorStatusCodeException) {
            HttpErrorStatusCodeException httpError = (HttpErrorStatusCodeException) failure;
            return httpError.isServerError() || isTransientClientError(httpError.getStatusCode());
        }
        Throwable cause = failure.getCause();
        if (cause != null && cause != failure) {
            return isLikelyTransientNetworkingIssue(cause);
        }
        return false;
    }

    private static boolean isTransientClientError(int statusCode) {
        return statusCode == HttpStatus.SC_REQUEST_TIMEOUT || statusCode == SC_TOO_MANY_REQUESTS;
    }

    public static <E extends Throwable> boolean isLikelyPermanentNetworkIssue(E failure) {
        if (failure instanceof HttpErrorStatusCodeException) {
            return isClientAuthenticationError(((HttpErrorStatusCodeException) failure).getStatusCode());
        }
        if (failure instanceof DefaultMultiCauseException) {
            List<? extends Throwable> causes = ((DefaultMultiCauseException) failure).getCauses();
            for (Throwable cause : causes) {
                if (isLikelyPermanentNetworkIssue(cause)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isClientAuthenticationError(int statusCode) {
        return statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN;
    }
}
