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

package org.gradle.internal.resource.transport.http;

import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.net.URI;

/**
 * A redirect strategy that follows redirects for all HTTP methods and preserves the original method.
 *
 * In HC5, the redirect executor changes POST/PUT to GET for 301/302/303 responses by default.
 * To preserve the original method (matching the HC4 behavior), this strategy upgrades 301/302/303
 * to 307 (Temporary Redirect) for unsafe methods before the redirect executor processes them.
 *
 * This was introduced to overcome a regression caused by switching to Apache HttpClient
 * as the transport mechanism for publishing (https://issues.gradle.org/browse/GRADLE-3312).
 */
public class AlwaysFollowAndPreserveMethodRedirectStrategy extends DefaultRedirectStrategy {

    @Override
    public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException {
        if (!Method.isSafe(request.getMethod())) {
            int code = response.getCode();
            if (code == HttpStatus.SC_MOVED_PERMANENTLY || code == HttpStatus.SC_MOVED_TEMPORARILY || code == HttpStatus.SC_SEE_OTHER) {
                response.setCode(HttpStatus.SC_TEMPORARY_REDIRECT);
            }
        }
        return super.getLocationURI(request, response, context);
    }
}
