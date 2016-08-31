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

package org.gradle.internal.resource.transport.aws.s3;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.credentials.AwsCredentials;
import org.gradle.authentication.Authentication;
import org.gradle.authentication.aws.AwsImAuthentication;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;

public class S3ConnectorFactory implements ResourceConnectorFactory {
    @Override
    public Set<String> getSupportedProtocols() {
        return Collections.singleton("s3");
    }

    @Override
    public Set<Class<? extends Authentication>> getSupportedAuthentication() {
        Set<Class<? extends Authentication>> supported = new HashSet<Class<? extends Authentication>>();
        supported.add(AwsImAuthentication.class);
        supported.add(AllSchemesAuthentication.class);
        return supported;
    }

    @Override
    public ExternalResourceConnector createResourceConnector(ResourceConnectorSpecification connectionDetails) {
        Collection<? extends Authentication> authentications = connectionDetails.getAuthentications();
        // Since s3 transport supports only one type of credentials at a time, let's use the first one found.
        for (Authentication authentication : authentications) {
            // We get only the first element here, nothing else. But Collection
            // forces us to use an iterator.
            if (authentication instanceof AllSchemesAuthentication) {
                // First things first, retro compatibility
                AwsCredentials awsCredentials = connectionDetails.getCredentials(AwsCredentials.class);
                if(awsCredentials == null) {
                    throw new IllegalArgumentException("AwsCredentials must be set for S3 backed repository.");
                }
                return new S3ResourceConnector(new S3Client(awsCredentials, new S3ConnectionProperties()));
            }

            if (authentication instanceof AwsImAuthentication) {
                return new S3ResourceConnector(new S3Client(new S3ConnectionProperties()));
            }
        }

        throw new IllegalArgumentException("S3 resource should either specify AwsIamAutentication or provide some AwsCredentials.");
    }
}
