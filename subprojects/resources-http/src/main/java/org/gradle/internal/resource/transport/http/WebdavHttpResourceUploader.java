/*
 * Copyright 2020 the original author or authors.
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

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.providers.webdav.WebDavWagon;
import org.apache.maven.wagon.repository.Repository;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.transfer.ExternalResourceUploader;

import java.net.URI;
import java.util.Collection;

public class WebdavHttpResourceUploader implements ExternalResourceUploader {

    private final Collection<Authentication> authentications;

    public WebdavHttpResourceUploader(Collection<Authentication> authentications) {
        this.authentications = authentications;
    }

    @Override
    public void upload(ReadableContent resource, URI destination) {
        String location = WebdavUtils.stripPrefix(destination.toString());
        Repository repository = new Repository("maven", location.substring(0, location.indexOf("/", location.indexOf("://") + 3)));
        WebDavWagon wagon = new WebDavWagon();

        try {
            wagon.connect(repository, getAuthenticationInfo());
            wagon.putFromStream(resource.open(), destination.getPath(), resource.getContentLength(), -1);
        } catch (ConnectionException e) {
            throw new RuntimeException("Connection error uploading to " + location, e);
        } catch (AuthenticationException e) {
            throw new RuntimeException("Authentication error uploading to " + location, e);
        } catch (TransferFailedException | ResourceDoesNotExistException e) {
            throw new RuntimeException("Transfer failed uploading to " + location, e);
        } catch (AuthorizationException e) {
            throw new RuntimeException("Authorization error uploading to " + location, e);
        }
    }

    private AuthenticationInfo getAuthenticationInfo() {
        return authentications.stream()
            .map(a -> (AuthenticationInternal) a)
            .map(AuthenticationInternal::getCredentials)
            .filter(c -> c instanceof PasswordCredentials)
            .map(c -> (PasswordCredentials) c)
            .map(WebdavHttpResourceUploader::toAuthenticationInfo)
            .findFirst()
            .orElse(null);
    }

    private static AuthenticationInfo toAuthenticationInfo(PasswordCredentials c) {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();

        authenticationInfo.setUserName(c.getUsername());
        authenticationInfo.setPassword(c.getPassword());

        return authenticationInfo;
    }
}
