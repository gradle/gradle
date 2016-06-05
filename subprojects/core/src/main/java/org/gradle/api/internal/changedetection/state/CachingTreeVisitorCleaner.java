/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.invocation.Gradle;
import org.gradle.internal.Factory;

import java.io.Closeable;
import java.io.IOException;

public class CachingTreeVisitorCleaner implements Closeable {
    private final Gradle gradle;
    private final TreeVisitorCacheExpirationStrategy cacheExpirationStrategy;

    public CachingTreeVisitorCleaner(CachingTreeVisitor cachingTreeVisitor, Gradle gradle) {
        this.gradle = gradle;
        cacheExpirationStrategy = new TreeVisitorCacheExpirationStrategy(cachingTreeVisitor, new Factory<OverlappingDirectoriesDetector>() {
            @Override
            public OverlappingDirectoriesDetector create() {
                return new OverlappingDirectoriesDetector();
            }
        });
        gradle.getTaskGraph().addTaskExecutionGraphListener(cacheExpirationStrategy);
        gradle.getTaskGraph().addTaskExecutionListener(cacheExpirationStrategy);
        gradle.addBuildListener(cacheExpirationStrategy);
    }

    @Override
    public void close() throws IOException {
        gradle.removeListener(cacheExpirationStrategy);
        gradle.getTaskGraph().removeTaskExecutionGraphListener(cacheExpirationStrategy);
        gradle.getTaskGraph().removeTaskExecutionListener(cacheExpirationStrategy);
    }

}
