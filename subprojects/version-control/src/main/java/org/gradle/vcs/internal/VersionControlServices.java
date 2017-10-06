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

package org.gradle.vcs.internal;

import org.gradle.cache.CacheRepository;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.vcs.SourceControl;

public class VersionControlServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new VersionControlGlobalServices());
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new VersionControlBuildTreeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new VersionControlBuildSessionServices());
    }

    private static class VersionControlGlobalServices {
        VcsMappingFactory createVcsMappingFactory() {
            return new DefaultVcsMappingFactory();
        }
    }

    private static class VersionControlBuildTreeServices {
        protected VcsMappingsInternal createVcsMappingsInternal(Instantiator instantiator) {
            return instantiator.newInstance(DefaultVcsMappings.class, instantiator);
        }

        protected SourceControl createSourceControl(Instantiator instantiator, VcsMappingsInternal vcsMappingsInternal) {
            return instantiator.newInstance(DefaultSourceControl.class, vcsMappingsInternal);
        }
    }

    private static class VersionControlBuildSessionServices {
        VersionControlSystemFactory createVersionControlSystemFactory(CacheRepository cacheRepository) {
            return new DefaultVersionControlSystemFactory(cacheRepository);
        }

    }
}
