/*
 * Copyright 2021 the original author or authors.
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

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.gradle.internal.verifier.HttpRedirectVerifier;

import java.util.Collections;

final class RedirectVerifyingStrategyDecorator implements RedirectStrategy {

    private final RedirectStrategy delegate;
    private final HttpRedirectVerifier verifier;

    public RedirectVerifyingStrategyDecorator(RedirectStrategy delegate, HttpRedirectVerifier verifier) {
        this.delegate = delegate;
        this.verifier = verifier;
    }

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        return delegate.isRedirected(request, response, context);
    }

    @Override
    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        HttpUriRequest redirectRequest = delegate.getRedirect(request, response, context);
        verifier.validateRedirects(Collections.singletonList(redirectRequest.getURI()));
        return redirectRequest;
    }
}
