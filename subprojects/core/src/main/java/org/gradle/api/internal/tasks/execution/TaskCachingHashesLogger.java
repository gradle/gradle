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

package org.gradle.api.internal.tasks.execution;

import com.google.common.hash.HashCode;
import org.gradle.api.Task;
import org.gradle.caching.BuildCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TaskCachingHashesLogger implements TaskCachingHashesListener {
    private final Logger logger = LoggerFactory.getLogger(SkipCachedTaskExecuter.class);

    @Override
    public void inputsCollected(Task task, BuildCacheKey key, TaskCachingInputs hashes) {
        logger.info("Cache key for {} is {}", task, key);
        logger.info("{} classloader hash: {}", task, hashes.getClassLoaderHash());
        logger.info("{} actions classloader hash: {}", task, hashes.getActionsClassLoaderHash());
        for (Map.Entry<String, HashCode> propertyHash : hashes.getInputHashes().entrySet()) {
            logger.info("{} input property '{}' hash: {}", task, propertyHash.getKey(), propertyHash.getValue());
        }
        logger.info("{} output property names: {}", task, hashes.getOutputPropertyNames());
    }
}
