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

package org.gradle.internal.resource.transport.gcs;

import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.LogManager;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class GcsConnectorFactory implements ResourceConnectorFactory {

    @Override
    public Set<String> getSupportedProtocols() {
        return Collections.singleton("gcs");
    }

    @Override
    public Set<Class<? extends Authentication>> getSupportedAuthentication() {
        Set<Class<? extends Authentication>> supported = new HashSet<Class<? extends Authentication>>();
        supported.add(AllSchemesAuthentication.class);
        return supported;
    }

    @Override
    public ExternalResourceConnector createResourceConnector(ResourceConnectorSpecification connectionDetails) {
        GcsResourceConnector resourceConnector;
        try {
            // Disable the default JUL logging which will log authentication tokens if its on here
            LogManager.getLogManager().reset();
            SLF4JBridgeHandler.install();
            resourceConnector = new GcsResourceConnector();
        } catch (Exception e) {
            throw new RuntimeException("Google Credentials must be set for GCS backed repository.", e);
        }
        return resourceConnector;
    }
}
