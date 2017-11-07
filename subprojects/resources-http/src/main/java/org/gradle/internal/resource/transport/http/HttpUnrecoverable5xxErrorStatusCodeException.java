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

package org.gradle.internal.resource.transport.http;

import org.gradle.internal.exceptions.Contextual;

/**
 * Signals that HTTP response has been received successfully but an unrecoverable 5xx error code is encountered (500 - 511).
 */
@Contextual
public class HttpUnrecoverable5xxErrorStatusCodeException extends HttpErrorStatusCodeException {

    public HttpUnrecoverable5xxErrorStatusCodeException(String method, String source, int statusCode, String reason) {
        super(method, source, statusCode, reason);
    }
}
