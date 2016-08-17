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

import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

import java.util.Iterator;
import java.util.Map;

public enum TaskFilePropertyCompareType implements TaskFilePropertyCompareStrategy {
    ORDERED(new OrderSensitiveTaskFilePropertyCompareStrategy()),
    UNORDERED(new OrderInsensitiveTaskFilePropertyCompareStrategy(true)),
    OUTPUT(new OrderInsensitiveTaskFilePropertyCompareStrategy(false));

    private final TaskFilePropertyCompareStrategy strategy;

    TaskFilePropertyCompareType(TaskFilePropertyCompareStrategy strategy) {
        this.strategy = strategy;
    }

    public Iterator<TaskStateChange> iterateContentChangesSince(Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String fileType) {
        return strategy.iterateContentChangesSince(current, previous, fileType);
    }

    public void appendToCacheKey(TaskCacheKeyBuilder builder, Map<String, NormalizedFileSnapshot> snapshots) {
        strategy.appendToCacheKey(builder, snapshots);
    }
}
