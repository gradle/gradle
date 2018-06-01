/*
 * Copyright 2018 the original author or authors.
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

import org.apache.http.Header;
import org.apache.http.auth.Credentials;
import org.apache.http.message.BasicHeader;

import java.security.Principal;

public class HttpClientHttpHeaderCredentials implements Credentials {

    private Header header;

    public void setHeader(String headerName, String headerValue) {
        this.header = new BasicHeader(headerName, headerValue);
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public void setHeader(String header) {
        if(header != null && header.contains(":")) {
            final String[] split = header.split(":", 2);
            this.setHeader(split[0], split[1].trim());
        }
    }

    public Header getHeader() {
        return header;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public String getPassword() {
        return null;
    }
}
