/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve.caching;

import com.google.common.collect.Maps;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.changedetection.state.Snapshot;
import org.gradle.api.internal.changedetection.state.ValueSnapshot;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.reflect.ConfigurableRule;
import org.gradle.internal.reflect.InstantiatingAction;

import java.io.Serializable;
import java.util.Map;

public class DefaultInMemoryCachingRuleExecutor<KEY, DETAILS, RESULT> implements CachingRuleExecutor<KEY, DETAILS, RESULT> {
    private final static Logger LOGGER = Logging.getLogger(DefaultInMemoryCachingRuleExecutor.class);

    private final Map<Snapshot, ResultWrapper<RESULT>> cache = Maps.newConcurrentMap();

    private final ValueSnapshotter snapshotter;
    private final Transformer<Serializable, KEY> ketToSnapshottable;

    public DefaultInMemoryCachingRuleExecutor(ValueSnapshotter snapshotter, Transformer<Serializable, KEY> ketToSnapshottable) {
        this.snapshotter = snapshotter;
        this.ketToSnapshottable = ketToSnapshottable;
    }

    @Override
    public <D extends DETAILS> RESULT execute(KEY key, InstantiatingAction<DETAILS> action, Transformer<RESULT, D> detailsToResult, Transformer<D, KEY> onCacheMiss, CachePolicy cachePolicy) {
        if (action == null) {
            return null;
        }
        ConfigurableRule<DETAILS> rule = action.getRule();
        ValueSnapshot snapshot = snapshotter.snapshot(
            new Object[]{
                ketToSnapshottable.transform(key),
                rule.getRuleClass(),
                rule.getRuleParams()
            }
        );
        ResultWrapper<RESULT> entry = cache.get(snapshot);
        if (entry != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found result for rule {} and key {} in in-memory cache", rule, key);
            }
            return entry.value;
        }
        D details = onCacheMiss.transform(key);
        action.execute(details);
        RESULT result = detailsToResult.transform(details);
        cache.put(snapshot, new ResultWrapper<RESULT>(result));
        return result;
    }

    private static class ResultWrapper<RESULT> {
        private final RESULT value;

        private ResultWrapper(RESULT value) {
            this.value = value;
        }
    }
}
