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

package org.gradle.internal.resource.transport.gcp.gcs;

import org.gradle.authentication.Authentication;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;

import java.util.Collections;
import java.util.Set;

import static java.util.Collections.emptySet;

public class GcsConnectorFactory implements ResourceConnectorFactory {

    @Override
    public Set<String> getSupportedProtocols() {
        return Collections.singleton("gcs");
    }

    @Override
    public Set<Class<? extends Authentication>> getSupportedAuthentication() {
        return emptySet();
    }

    @Override
    public ExternalResourceConnector createResourceConnector(ResourceConnectorSpecification connectionDetails) {
        try {
            return new GcsResourceConnector(GcsClient.create(new GcsConnectionProperties()));
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
