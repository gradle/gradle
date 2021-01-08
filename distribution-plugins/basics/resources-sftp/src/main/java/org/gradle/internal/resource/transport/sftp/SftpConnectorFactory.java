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

package org.gradle.internal.resource.transport.sftp;

import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.transfer.DefaultExternalResourceConnector;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SftpConnectorFactory implements ResourceConnectorFactory {
    private final SftpClientFactory sftpClientFactory;

    public SftpConnectorFactory(SftpClientFactory sftpClientFactory) {
        this.sftpClientFactory = sftpClientFactory;
    }

    @Override
    public Set<String> getSupportedProtocols() {
        return Collections.singleton("sftp");
    }

    @Override
    public Set<Class<? extends Authentication>> getSupportedAuthentication() {
        Set<Class<? extends Authentication>> supported = new HashSet<Class<? extends Authentication>>();
        supported.add(AllSchemesAuthentication.class);
        return supported;
    }

    @Override
    public ExternalResourceConnector createResourceConnector(ResourceConnectorSpecification connectionDetails) {
        PasswordCredentials passwordCredentials = connectionDetails.getCredentials(PasswordCredentials.class);
        SftpResourceAccessor accessor = new SftpResourceAccessor(sftpClientFactory, passwordCredentials);
        SftpResourceLister lister = new SftpResourceLister(sftpClientFactory, passwordCredentials);
        SftpResourceUploader uploader = new SftpResourceUploader(sftpClientFactory, passwordCredentials);
        return new DefaultExternalResourceConnector(accessor, lister, uploader);
    }
}
