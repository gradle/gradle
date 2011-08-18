/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;

import java.io.File;

public class EventBroadcastingIvyService implements IvyService {
    private final IvyService ivyService;

    public EventBroadcastingIvyService(IvyService ivyService) {
        this.ivyService = ivyService;
    }

    public ResolvedConfiguration resolve(ConfigurationInternal configuration) throws ResolveException {
        DependencyResolutionListener broadcast = configuration.getDependencyResolutionBroadcast();
        ResolvableDependencies dependencies = configuration.getIncoming();
        broadcast.beforeResolve(dependencies);
        try {
            return ivyService.resolve(configuration);
        } finally {
            broadcast.afterResolve(dependencies);
        }
    }

    public void publish(Configuration configuration, File descriptorDestination) throws PublishException {
        ivyService.publish(configuration, descriptorDestination);
    }
}
