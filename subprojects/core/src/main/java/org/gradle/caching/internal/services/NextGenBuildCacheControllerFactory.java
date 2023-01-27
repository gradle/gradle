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

package org.gradle.caching.internal.services;

import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.NoOpBuildCacheService;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.DefaultNextGenBuildCacheAccess;
import org.gradle.caching.internal.controller.NextGenBuildCacheController;
import org.gradle.caching.local.internal.H2LocalCacheService;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;

import java.nio.file.Path;

public final class NextGenBuildCacheControllerFactory {

    public static BuildCacheController create(
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        BuildCacheConfigurationInternal buildCacheConfiguration,
        Instantiator instantiator
    ) {
        // DirectoryBuildCache local = buildCacheConfiguration.getLocal();
        BuildCache remote = buildCacheConfiguration.getRemote();
        boolean remoteEnabled = remote != null && remote.isEnabled();

        Path localCacheLocation = cacheBuilderFactory.baseDirForCrossVersionCache("build-cache-2").toPath();
        BuildCacheService localService = new H2LocalCacheService(localCacheLocation);

        BuildCacheService remoteService = remoteEnabled
            ? createBuildCacheService(remote, buildCacheConfiguration, instantiator)
            : NoOpBuildCacheService.INSTANCE;

        return new NextGenBuildCacheController(
            new DefaultNextGenBuildCacheAccess(
                localService, remoteService
            )
        );
    }

    private static BuildCacheService createBuildCacheService(
        BuildCache configuration,
        BuildCacheConfigurationInternal buildCacheConfiguration,
        Instantiator instantiator
    ) {
        Class<? extends BuildCacheServiceFactory<BuildCache>> castFactoryType = Cast.uncheckedNonnullCast(
            buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass())
        );

        BuildCacheServiceFactory<BuildCache> factory = instantiator.newInstance(castFactoryType);
        Describer describer = new Describer();
        return factory.createBuildCacheService(configuration, describer);
    }

    private static class Describer implements BuildCacheServiceFactory.Describer {

        @Override
        public BuildCacheServiceFactory.Describer type(String type) {
            return this;
        }

        @Override
        public BuildCacheServiceFactory.Describer config(String name, String value) {
            return this;
        }
    }
}
