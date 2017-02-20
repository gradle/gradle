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

import org.gradle.StartParameter;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.progress.BuildOperationExecutor;

import java.io.IOException;

public class DefaultBuildCacheServiceProvider implements BuildCacheServiceProvider {
    private final BuildCacheConfigurationInternal buildCacheConfiguration;
    private final StartParameter startParameter;
    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultBuildCacheServiceProvider(BuildCacheConfigurationInternal buildCacheConfiguration, StartParameter startParameter, BuildOperationExecutor buildOperationExecutor) {
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.startParameter = startParameter;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public BuildCacheService getBuildCacheService() {
        BuildCache configuration = selectConfiguration();
        if (configuration != null) {
            return new LenientBuildCacheServiceDecorator(
                new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(
                    3,
                    new LoggingBuildCacheServiceDecorator(
                        new PushOrPullPreventingBuildCacheServiceDecorator(
                            startParameter,
                            configuration,
                            new BuildOperationFiringBuildCacheServiceDecorator(
                                buildOperationExecutor,
                                createBuildCacheService(configuration)
                            )
                        )
                    )
                )
            );
        } else {
            return new NoOpBuildCacheService();
        }
    }

    private BuildCache selectConfiguration() {
        if (startParameter.isTaskOutputCacheEnabled()) {
            BuildCache remote = buildCacheConfiguration.getRemote();
            BuildCache local = buildCacheConfiguration.getLocal();
            if (remote != null && remote.isEnabled()) {
                return remote;
            } else if (local.isEnabled()) {
                return local;
            }
        }
        return null;
    }

    private <T extends BuildCache> BuildCacheService createBuildCacheService(final T configuration) {
        Class<? extends T> configurationType = Cast.uncheckedCast(configuration.getClass());
        BuildCacheServiceFactory<T> factory = buildCacheConfiguration.getFactory(configurationType);
        return factory.build(configuration);
    }

    private static class NoOpBuildCacheService implements BuildCacheService {
        @Override
        public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
            return false;
        }

        @Override
        public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        }

        @Override
        public String getDescription() {
            return "NO-OP build cache";
        }

        @Override
        public void close() throws IOException {
            // Do nothing
        }
    }
}
