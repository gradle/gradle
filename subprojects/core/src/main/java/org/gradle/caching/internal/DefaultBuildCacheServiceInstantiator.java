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

package org.gradle.caching.internal;

import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;

public class DefaultBuildCacheServiceInstantiator implements BuildCacheServiceInstantiator {
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildCacheConfigurationInternal buildCacheConfiguration;
    private final Instantiator instantiator;

    public DefaultBuildCacheServiceInstantiator(BuildCacheConfigurationInternal buildCacheConfiguration, BuildOperationExecutor buildOperationExecutor, Instantiator instantiator) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.instantiator = instantiator;
    }

    @Override
    public BuildCacheService createBuildCacheService(BuildCache configuration, boolean pullDisabled, boolean pushDisabled) {
        return decorateBuildCacheService(pullDisabled, pushDisabled, instantiateService(configuration));
    }

    private BuildCacheService decorateBuildCacheService(boolean pullDisabled, boolean pushDisabled, BuildCacheService buildCacheService) {
        return new LenientBuildCacheServiceDecorator(
            new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(
                3,
                new LoggingBuildCacheServiceDecorator(
                    new PushOrPullPreventingBuildCacheServiceDecorator(
                        pushDisabled,
                        pullDisabled,
                        new BuildOperationFiringBuildCacheServiceDecorator(
                            buildOperationExecutor,
                            buildCacheService
                        )
                    )
                )
            )
        );
    }

    private <T extends BuildCache> BuildCacheService instantiateService(final T configuration) {
        Class<? extends BuildCacheServiceFactory<T>> buildCacheServiceFactoryType = Cast.uncheckedCast(buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass()));
        return instantiator.newInstance(buildCacheServiceFactoryType).build(configuration);
    }
}
