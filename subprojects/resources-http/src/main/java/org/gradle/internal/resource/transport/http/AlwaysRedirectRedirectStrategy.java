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

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;

import java.net.URI;

/**
 * A class which makes httpclient follow redirects for all http methods.
 * This has been introduced to overcome a regression caused by switching to apache httpclient as the transport mechanism for publishing (https://issues.gradle.org/browse/GRADLE-3312)
 * The rational for httpclient not following redirects, by default, can be found here: https://issues.apache.org/jira/browse/HTTPCLIENT-860
 */
public class AlwaysRedirectRedirectStrategy extends DefaultRedirectStrategy {

    public AlwaysRedirectRedirectStrategy() {
    }

    @Override
    protected boolean isRedirectable(String method) {
        return true;
    }

    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        URI uri = this.getLocationURI(request, response, context);
        String method = request.getRequestLine().getMethod();
        if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
            return new HttpHead(uri);
        } else if (method.equalsIgnoreCase(HttpPost.METHOD_NAME)) {
            return this.copyEntity(new HttpPost(uri), request);
        } else if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
            return this.copyEntity(new HttpPut(uri), request);
        } else if (method.equalsIgnoreCase(HttpDelete.METHOD_NAME)) {
            return new HttpDelete(uri);
        } else if (method.equalsIgnoreCase(HttpTrace.METHOD_NAME)) {
            return new HttpTrace(uri);
        } else if (method.equalsIgnoreCase(HttpOptions.METHOD_NAME)) {
            return new HttpOptions(uri);
        } else if (method.equalsIgnoreCase(HttpPatch.METHOD_NAME)) {
            return this.copyEntity(new HttpPatch(uri), request);
        } else {
            return new HttpGet(uri);
        }
    }

    private HttpUriRequest copyEntity(HttpEntityEnclosingRequestBase redirect, HttpRequest original) {
        if (original instanceof HttpEntityEnclosingRequest) {
            redirect.setEntity(((HttpEntityEnclosingRequest) original).getEntity());
        }
        return redirect;
    }
}
