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

package org.gradle.internal.resource.transport.file;

import com.google.common.collect.Sets;
import org.gradle.authentication.Authentication;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;

import java.util.Set;

public class FileConnectorFactory implements ResourceConnectorFactory {
    @Override
    public Set<String> getSupportedProtocols() {
        return Sets.newHashSet("file");
    }

    @Override
    public Set<Class<? extends Authentication>> getSupportedAuthentication() {
        return Sets.newHashSet();
    }

    @Override
    public ExternalResourceConnector createResourceConnector(ResourceConnectorSpecification connectionDetails) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
