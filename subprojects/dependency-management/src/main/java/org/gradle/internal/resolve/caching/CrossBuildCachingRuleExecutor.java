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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.DependencyInjectingInstantiator;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.SnapshotSerializer;
import org.gradle.api.internal.changedetection.state.ValueSnapshot;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.internal.reflect.ConfigurableRule;
import org.gradle.internal.reflect.InstantiatingAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.BuildCommencedTimeProvider;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

public class CrossBuildCachingRuleExecutor<KEY, DETAILS, RESULT> implements CachingRuleExecutor<KEY, DETAILS, RESULT>, Closeable {
    private final static Logger LOGGER = Logging.getLogger(CrossBuildCachingRuleExecutor.class);

    private final ValueSnapshotter snapshotter;
    private final Transformer<Serializable, KEY> ketToSnapshottable;
    private final PersistentCache cache;
    private final PersistentIndexedCache<ValueSnapshot, CachedEntry<RESULT>> store;
    private final BuildCommencedTimeProvider timeProvider;
    private final EntryValidator<RESULT> validator;

    public CrossBuildCachingRuleExecutor(String name,
                                         CacheRepository cacheRepository,
                                         InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                         ValueSnapshotter snapshotter,
                                         BuildCommencedTimeProvider timeProvider,
                                         EntryValidator<RESULT> validator,
                                         Transformer<Serializable, KEY> ketToSnapshottable,
                                         Serializer<RESULT> resultSerializer) {
        this.snapshotter = snapshotter;
        this.validator = validator;
        this.ketToSnapshottable = ketToSnapshottable;
        this.timeProvider = timeProvider;
        this.cache = cacheRepository
            .cache(name)
            .withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.None))
            .open();
        PersistentIndexedCacheParameters<ValueSnapshot, CachedEntry<RESULT>> cacheParams = createCacheConfiguration(name, resultSerializer, cacheDecoratorFactory);
        this.store = this.cache.createCache(cacheParams);
    }

    private PersistentIndexedCacheParameters<ValueSnapshot, CachedEntry<RESULT>> createCacheConfiguration(String name, Serializer<RESULT> resultSerializer, InMemoryCacheDecoratorFactory cacheDecoratorFactory) {
        Serializer<ValueSnapshot> snapshotSerializer = new SnapshotSerializer();

        PersistentIndexedCacheParameters<ValueSnapshot, CachedEntry<RESULT>> cacheParams = new PersistentIndexedCacheParameters<ValueSnapshot, CachedEntry<RESULT>>(
            name,
            snapshotSerializer,
            createEntrySerializer(resultSerializer)
        );
        cacheParams.cacheDecorator(cacheDecoratorFactory.decorator(2000, true));
        return cacheParams;
    }

    private Serializer<CachedEntry<RESULT>> createEntrySerializer(final Serializer<RESULT> resultSerializer) {
        return new CacheEntrySerializer<RESULT>(resultSerializer);
    }

    @Override
    public <D extends DETAILS> RESULT execute(final KEY key, final InstantiatingAction<DETAILS> action, final Transformer<RESULT, D> detailsToResult, final Transformer<D, KEY> onCacheMiss, final CachePolicy cachePolicy) {
        if (action == null) {
            return null;
        }
        final ConfigurableRule<DETAILS> rule = action.getRule();
        if (rule.isCacheable()) {
            return tryFromCache(key, action, detailsToResult, onCacheMiss, cachePolicy, rule);
        } else {
            return executeRule(key, action, detailsToResult, onCacheMiss);
        }
    }

    private <D extends DETAILS> RESULT tryFromCache(KEY key, InstantiatingAction<DETAILS> action, Transformer<RESULT, D> detailsToResult, Transformer<D, KEY> onCacheMiss, CachePolicy cachePolicy, ConfigurableRule<DETAILS> rule) {
        List<Object> toBeSnapshotted = Lists.newArrayListWithExpectedSize(4);
        toBeSnapshotted.add(ketToSnapshottable.transform(key));
        Class<? extends Action<DETAILS>> ruleClass = rule.getRuleClass();
        Object[] ruleParams = rule.getRuleParams();

        toBeSnapshotted.add(ruleClass);
        toBeSnapshotted.add(ruleParams);
        Instantiator instantiator = action.getInstantiator();
        if (instantiator instanceof DependencyInjectingInstantiator) {
            toBeSnapshotted.addAll(((DependencyInjectingInstantiator) instantiator).identifyInjectedServices(ruleClass, ruleParams));
        }
        final ValueSnapshot snapshot = snapshotter.snapshot(toBeSnapshotted);
        CachedEntry<RESULT> entry = store.get(snapshot);
        if (entry != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found result for rule {} and key {} in cache", rule, key);
            }
            if (validator.isValid(cachePolicy, entry)) {
                return entry.getResult();
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Invalidating result for rule {} and key {} in cache", rule, key);
            }
        }

        RESULT result = executeRule(key, action, detailsToResult, onCacheMiss);
        if (result != null) {
            store.put(snapshot, new CachedEntry<RESULT>(timeProvider.getCurrentTime(), result));
        }
        return result;
    }

    private <D extends DETAILS> RESULT executeRule(KEY key, InstantiatingAction<DETAILS> action, Transformer<RESULT, D> detailsToResult, Transformer<D, KEY> onCacheMiss) {
        D details = onCacheMiss.transform(key);
        action.execute(details);
        return detailsToResult.transform(details);
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }

    public static class CachedEntry<RESULT> {
        private final long timestamp;
        private final RESULT result;

        private CachedEntry(long timestamp, RESULT result) {
            this.timestamp = timestamp;
            this.result = result;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public RESULT getResult() {
            return result;
        }
    }

    /**
     * When getting a result from the cache, we need to check whether the
     * result is still valid or not. We cannot take that decision before
     * knowing the actual type of KEY, so we need to provide this as a
     * pluggable strategy when creating the executor.
     * @param <RESULT> the type of entry stored in the cache.
     */
    public interface EntryValidator<RESULT> {
        boolean isValid(CachePolicy policy, CachedEntry<RESULT> entry);
    }

    private static class CacheEntrySerializer<RESULT> extends AbstractSerializer<CachedEntry<RESULT>> {
        private final Serializer<RESULT> resultSerializer;

        public CacheEntrySerializer(Serializer<RESULT> resultSerializer) {
            this.resultSerializer = resultSerializer;
        }

        @Override
        public CachedEntry<RESULT> read(Decoder decoder) throws Exception {
            return new CachedEntry<RESULT>(decoder.readLong(), resultSerializer.read(decoder));
        }

        @Override
        public void write(Encoder encoder, CachedEntry<RESULT> value) throws Exception {
            encoder.writeLong(value.timestamp);
            resultSerializer.write(encoder, value.result);
        }
    }
}
