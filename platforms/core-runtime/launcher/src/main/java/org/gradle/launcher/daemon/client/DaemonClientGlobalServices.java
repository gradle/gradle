/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.client;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.tooling.internal.provider.action.BuildActionSerializer;

/**
 * Global services shared by all Gradle daemon clients in a given process.
 */
public class DaemonClientGlobalServices implements ServiceRegistrationProvider {
    @Provides
    Serializer<BuildAction> createBuildActionSerializer() {
        return BuildActionSerializer.create();
    }

    @Provides
    DaemonGreeter createDaemonGreeter(DocumentationRegistry documentationRegistry) {
        return new DaemonGreeter(documentationRegistry);
    }

    @Provides
    DaemonClientFactory createClientFactory(ServiceRegistry sharedServices) {
        return new DaemonClientFactory(sharedServices);
    }
}
