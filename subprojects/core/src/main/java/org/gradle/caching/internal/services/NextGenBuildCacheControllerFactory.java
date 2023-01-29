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
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
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
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.io.IOException;

public final class NextGenBuildCacheControllerFactory {

    public static BuildCacheController create(
        BuildCacheConfigurationInternal buildCacheConfiguration,
        Deleter deleter,
        FileSystemAccess fileSystemAccess,
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        Instantiator instantiator,
        PathToFileResolver resolver
    ) {
        // DirectoryBuildCache local = buildCacheConfiguration.getLocal();
        BuildCache remote = buildCacheConfiguration.getRemote();
        boolean remoteEnabled = remote != null && remote.isEnabled();

        Object cacheDirectory = buildCacheConfiguration.getLocal().getDirectory();
        File target;
        if (cacheDirectory != null) {
            target = resolver.resolve(cacheDirectory);
        } else {
            target = cacheBuilderFactory.baseDirForCrossVersionCache("build-cache-2");
        }
        BuildCacheService localService = new H2LocalCacheService(target.toPath());

        BuildCacheService remoteService = remoteEnabled
            ? createBuildCacheService(remote, buildCacheConfiguration, instantiator)
            : NoOpBuildCacheService.INSTANCE;

        return new NextGenBuildCacheController(
            deleter,
            fileSystemAccess,
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
        BuildCacheService service = factory.createBuildCacheService(configuration, describer);
        return configuration.isPush()
            ? service
            : new NoPushBuildCacheService(service);
    }

    private static class NoPushBuildCacheService implements BuildCacheService {
        private final BuildCacheService delegate;

        public NoPushBuildCacheService(BuildCacheService delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
            return delegate.load(key, reader);
        }

        @Override
        public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
            // Do nothing
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
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
