/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.testfixtures.internal.TestInMemoryCacheFactory;

import java.io.File;

/**
 * Static util class for obtaining test doubles for a {@link DecompressionCoordinator}.
 */
public abstract class TestCaches {
    private TestCaches() {}

    public static DecompressionCoordinator decompressionCache(File cacheDir) {
        final TestInMemoryCacheFactory cacheFactory = new TestInMemoryCacheFactory();
        return new DefaultDecompressionCoordinator(cacheFactory.open(cacheDir, "test compression cache"));
    }
}
