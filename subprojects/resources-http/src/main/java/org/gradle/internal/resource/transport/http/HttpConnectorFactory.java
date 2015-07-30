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

import com.google.common.collect.Sets;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.params.AuthPolicy;
import org.gradle.api.authentication.Authentication;
import org.gradle.api.authentication.BasicAuthentication;
import org.gradle.api.authentication.DigestAuthentication;
import org.gradle.api.authentication.NtlmAuthentication;
import org.gradle.internal.resource.PasswordCredentials;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.transfer.DefaultExternalResourceConnector;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class HttpConnectorFactory implements ResourceConnectorFactory {
    @Override
    public Set<String> getSupportedProtocols() {
        return Sets.newHashSet("http", "https");
    }

    @Override
    public Set<Class<? extends Authentication>> getSupportedAuthentication() {
        Set<Class<? extends Authentication>> supported = new HashSet<Class<? extends Authentication>>();
        supported.add(BasicAuthentication.class);
        supported.add(DigestAuthentication.class);
        supported.add(NtlmAuthentication.class);
        return supported;
    }

    @Override
    public ExternalResourceConnector createResourceConnector(ResourceConnectorSpecification connectionDetails) {
        Set<String> authSchemes = getAuthSchemes(connectionDetails.getAuthentications());
        HttpClientHelper http = new HttpClientHelper(new DefaultHttpSettings(connectionDetails.getCredentials(PasswordCredentials.class), authSchemes));
        HttpResourceAccessor accessor = new HttpResourceAccessor(http);
        HttpResourceLister lister = new HttpResourceLister(accessor);
        HttpResourceUploader uploader = new HttpResourceUploader(http);
        return new DefaultExternalResourceConnector(accessor, lister, uploader);
    }

    private Set<String> getAuthSchemes(Collection<Authentication> authenticationTypes) {
        Set<String> authSchemes = Sets.newHashSet();
        for (Authentication authenticationType : authenticationTypes) {
            if (BasicAuthentication.class.isAssignableFrom(authenticationType.getClass())) {
                authSchemes.add(AuthPolicy.BASIC);
            } else if (DigestAuthentication.class.isAssignableFrom(authenticationType.getClass())) {
                authSchemes.add(AuthPolicy.DIGEST);
            } else if (NtlmAuthentication.class.isAssignableFrom(authenticationType.getClass())) {
                authSchemes.add(AuthPolicy.NTLM);
            } else {
                throw new IllegalArgumentException(String.format("Authentication type of '%s' is not supported.", authenticationType.getClass().getSimpleName()));
            }
        }

        if (authSchemes.size() == 0) {
            authSchemes.add(AuthScope.ANY_SCHEME);
        }

        return authSchemes;
    }
}
