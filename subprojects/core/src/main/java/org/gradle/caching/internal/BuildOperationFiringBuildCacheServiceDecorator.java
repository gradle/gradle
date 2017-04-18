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

import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BuildOperationFiringBuildCacheServiceDecorator extends AbstractRoleAwareBuildCacheServiceDecorator {
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationFiringBuildCacheServiceDecorator(BuildOperationExecutor buildOperationExecutor, RoleAwareBuildCacheService delegate) {
        super(delegate);
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public boolean load(final BuildCacheKey key, final BuildCacheEntryReader reader) throws BuildCacheException {
        return super.load(key, new BuildOperationFiringBuildCacheEntryReader(reader, key));
    }

    @Override
    public void store(final BuildCacheKey key, final BuildCacheEntryWriter writer) throws BuildCacheException {
        super.store(key, new BuildOperationFiringBuildCacheEntryWriter(writer, key));
    }

    private class BuildOperationFiringBuildCacheEntryReader implements BuildCacheEntryReader {
        private final BuildCacheEntryReader delegate;
        private final BuildCacheKey key;

        private BuildOperationFiringBuildCacheEntryReader(BuildCacheEntryReader delegate, BuildCacheKey key) {
            this.delegate = delegate;
            this.key = key;
        }

        @Override
        public void readFrom(final InputStream input) throws IOException {
            buildOperationExecutor.run(new RunnableBuildOperation() {
               @Override
               public void run(BuildOperationContext buildOperationContext) {
                   try {
                       delegate.readFrom(input);
                   } catch (IOException e) {
                       buildOperationContext.failed(e);
                   }
               }

               @Override
               public BuildOperationDescriptor.Builder description() {
                   return BuildOperationDescriptor.displayName("Load entry " + key + " from " + getRole() + " build cache");
               }
           });
        }
    }

    private class BuildOperationFiringBuildCacheEntryWriter implements BuildCacheEntryWriter {
        private final BuildCacheEntryWriter delegate;
        private final BuildCacheKey key;

        private BuildOperationFiringBuildCacheEntryWriter(BuildCacheEntryWriter delegate, BuildCacheKey key) {
            this.delegate = delegate;
            this.key = key;
        }

        @Override
        public void writeTo(final OutputStream output) throws IOException {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext buildOperationContext) {
                    try {
                        delegate.writeTo(output);
                    } catch (IOException e) {
                        buildOperationContext.failed(e);
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Store entry " + key + " in " + getRole() + " build cache");
                }
            });
        }
    }
}
