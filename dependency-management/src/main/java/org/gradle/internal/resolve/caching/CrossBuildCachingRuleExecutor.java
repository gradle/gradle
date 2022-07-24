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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.cache.scopes.GlobalScopedCache;
import org.gradle.internal.Cast;
import org.gradle.internal.action.ConfigurableRule;
import org.gradle.internal.action.ConfigurableRules;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CrossBuildCachingRuleExecutor<KEY, DETAILS, RESULT> implements CachingRuleExecutor<KEY, DETAILS, RESULT>, Closeable {
    private final static Logger LOGGER = Logging.getLogger(CrossBuildCachingRuleExecutor.class);

    private final ValueSnapshotter snapshotter;
    private final Transformer<?, KEY> keyToSnapshottable;
    private final PersistentCache cache;
    private final PersistentIndexedCache<HashCode, CachedEntry<RESULT>> store;
    private final BuildCommencedTimeProvider timeProvider;
    private final EntryValidator<RESULT> validator;

    public CrossBuildCachingRuleExecutor(String name,
                                         GlobalScopedCache cacheRepository,
                                         InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                         ValueSnapshotter snapshotter,
                                         BuildCommencedTimeProvider timeProvider,
                                         EntryValidator<RESULT> validator,
                                         Transformer<?, KEY> keyToSnapshottable,
                                         Serializer<RESULT> resultSerializer) {
        this.snapshotter = snapshotter;
        this.validator = validator;
        this.keyToSnapshottable = keyToSnapshottable;
        this.timeProvider = timeProvider;
        this.cache = cacheRepository
            .cache(name)
            .withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.OnDemand))
            .open();
        PersistentIndexedCacheParameters<HashCode, CachedEntry<RESULT>> cacheParams = createCacheConfiguration(name, resultSerializer, cacheDecoratorFactory);
        this.store = this.cache.createCache(cacheParams);
    }

    private PersistentIndexedCacheParameters<HashCode, CachedEntry<RESULT>> createCacheConfiguration(String name, Serializer<RESULT> resultSerializer, InMemoryCacheDecoratorFactory cacheDecoratorFactory) {
        return PersistentIndexedCacheParameters.of(
            name,
            new HashCodeSerializer(),
            createEntrySerializer(resultSerializer)
        ).withCacheDecorator(
            cacheDecoratorFactory.decorator(2000, true)
        );
    }

    private Serializer<CachedEntry<RESULT>> createEntrySerializer(final Serializer<RESULT> resultSerializer) {
        return new CacheEntrySerializer<>(resultSerializer);
    }

    @Override
    public <D extends DETAILS> RESULT execute(final KEY key, final InstantiatingAction<DETAILS> action, final Transformer<RESULT, D> detailsToResult, final Transformer<D, KEY> onCacheMiss, final CachePolicy cachePolicy) {
        if (action == null) {
            return null;
        }
        final ConfigurableRules<DETAILS> rules = action.getRules();
        if (rules.isCacheable()) {
            return tryFromCache(key, action, detailsToResult, onCacheMiss, cachePolicy, rules);
        } else {
            return executeRule(key, action, detailsToResult, onCacheMiss);
        }
    }

    private <D extends DETAILS> RESULT tryFromCache(KEY key, InstantiatingAction<DETAILS> action, Transformer<RESULT, D> detailsToResult, Transformer<D, KEY> onCacheMiss, CachePolicy cachePolicy, ConfigurableRules<DETAILS> rules) {
        final HashCode keyHash = computeExplicitInputsSnapshot(key, rules);
        DefaultImplicitInputRegistrar registrar = new DefaultImplicitInputRegistrar();
        ImplicitInputsCapturingInstantiator instantiator = findInputCapturingInstantiator(action);
        if (instantiator != null) {
            action = action.withInstantiator(instantiator.capturing(registrar));
        }
        // First step is to find an entry with the explicit inputs in the cache
        CachedEntry<RESULT> entry = store.getIfPresent(keyHash);
        if (entry != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Found result for rule {} and key {} in cache", rules, key);
            }
            if (validator.isValid(cachePolicy, entry) && areImplicitInputsUpToDate(instantiator, key, rules, entry)) {
                // Here it means that we have validated that the entry is still up-to-date, and that means a couple of things:
                // 1. the cache policy said that the entry is still valid (for example, `--refresh-dependencies` wasn't called)
                // 2. if the rule is cacheable, we have validated that its discovered inputs are still the same
                return entry.getResult();
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Invalidating result for rule {} and key {} in cache", rules, key);
            }
        }

        RESULT result = executeRule(key, action, detailsToResult, onCacheMiss);
        store.put(keyHash, new CachedEntry<>(timeProvider.getCurrentTime(), registrar.implicits, result));
        return result;
    }

    /**
     * This method computes a snapshot of the explicit inputs of the rule, which consist of the rule implementation,
     * the rule key (for example, a module identifier) and the optional rule parameters.
     * @param key the primary key
     * @param rules the rules to be snapshotted
     * @return a snapshot of the inputs
     */
    private HashCode computeExplicitInputsSnapshot(KEY key, ConfigurableRules<DETAILS> rules) {
        List<Object> toBeSnapshotted = Lists.newArrayListWithExpectedSize(2 + 2 * rules.getConfigurableRules().size());
        toBeSnapshotted.add(keyToSnapshottable.transform(key));
        for (ConfigurableRule<DETAILS> rule : rules.getConfigurableRules()) {
            Class<? extends Action<DETAILS>> ruleClass = rule.getRuleClass();
            Isolatable<Object[]> ruleParams = rule.getRuleParams();
            toBeSnapshotted.add(ruleClass);
            toBeSnapshotted.add(ruleParams);
        }
        Hasher hasher = Hashing.newHasher();
        snapshotter.snapshot(toBeSnapshotted).appendToHasher(hasher);
        return hasher.hash();
    }

    private ImplicitInputsCapturingInstantiator findInputCapturingInstantiator(InstantiatingAction<DETAILS> action) {
        Instantiator instantiator = action.getInstantiator();
        if (instantiator instanceof ImplicitInputsCapturingInstantiator) {
            return (ImplicitInputsCapturingInstantiator) instantiator;
        }
        return null;
    }

    private boolean areImplicitInputsUpToDate(ImplicitInputsCapturingInstantiator serviceRegistry, KEY key, ConfigurableRules<DETAILS> rules, CachedEntry<RESULT> entry) {
        for (Map.Entry<String, Collection<ImplicitInputRecord<?, ?>>> implicitEntry : entry.getImplicits().asMap().entrySet()) {
            String serviceName = implicitEntry.getKey();
            ImplicitInputsProvidingService<Object, Object, ?> provider = Cast.uncheckedCast(serviceRegistry.findInputCapturingServiceByName(serviceName));
            for (ImplicitInputRecord<?, ?> list : implicitEntry.getValue()) {
                if (!provider.isUpToDate(list.getInput(), list.getOutput())) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Invalidating result for rule {} and key {} in cache because implicit input provided by service {} changed", rules, key, provider.getClass());
                    }
                    return false;
                }
            }
        }
        return true;
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
        private final Multimap<String, ImplicitInputRecord<?, ?>> implicits;
        private final RESULT result;

        private CachedEntry(long timestamp, Multimap<String, ImplicitInputRecord<?, ?>> implicits, RESULT result) {
            this.timestamp = timestamp;
            this.implicits = implicits;
            this.result = result;
        }

        public Multimap<String, ImplicitInputRecord<?, ?>> getImplicits() {
            return implicits;
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
     *
     * @param <RESULT> the type of entry stored in the cache.
     */
    public interface EntryValidator<RESULT> {
        boolean isValid(CachePolicy policy, CachedEntry<RESULT> entry);
    }

    private static class CacheEntrySerializer<RESULT> extends AbstractSerializer<CachedEntry<RESULT>> {
        private final Serializer<RESULT> resultSerializer;
        private final AnySerializer anySerializer = new AnySerializer();

        public CacheEntrySerializer(Serializer<RESULT> resultSerializer) {
            this.resultSerializer = resultSerializer;
        }

        @Override
        public CachedEntry<RESULT> read(Decoder decoder) throws Exception {
            return new CachedEntry<>(decoder.readLong(), readImplicits(decoder), resultSerializer.read(decoder));
        }

        private Multimap<String, ImplicitInputRecord<?, ?>> readImplicits(Decoder decoder) throws Exception {
            int cpt = decoder.readSmallInt();
            Multimap<String, ImplicitInputRecord<?, ?>> result = HashMultimap.create();
            for (int i = 0; i < cpt; i++) {
                String impl = decoder.readString();
                List<ImplicitInputRecord<?, ?>> implicitInputOutputs = readImplicitList(decoder);
                result.putAll(impl, implicitInputOutputs);
            }
            return result;
        }

        List<ImplicitInputRecord<?, ?>> readImplicitList(Decoder decoder) throws Exception {
            int cpt = decoder.readSmallInt();
            List<ImplicitInputRecord<?, ?>> implicits = Lists.newArrayListWithCapacity(cpt);
            for (int i = 0; i < cpt; i++) {
                final Object in = readAny(decoder);
                final Object out = readAny(decoder);
                implicits.add(new ImplicitInputRecord<Object, Object>() {
                    @Override
                    public Object getInput() {
                        return in;
                    }

                    @Nullable
                    @Override
                    public Object getOutput() {
                        return out;
                    }
                });
            }
            return implicits;
        }

        @Nullable
        private Object readAny(Decoder decoder) throws Exception {
            return anySerializer.read(decoder);
        }

        @Override
        public void write(Encoder encoder, CachedEntry<RESULT> value) throws Exception {
            encoder.writeLong(value.timestamp);
            writeImplicits(encoder, value.implicits);
            resultSerializer.write(encoder, value.result);
        }

        private void writeImplicits(Encoder encoder, Multimap<String, ImplicitInputRecord<?, ?>> implicits) throws Exception {
            encoder.writeSmallInt(implicits.size());
            for (Map.Entry<String, Collection<ImplicitInputRecord<?, ?>>> entry : implicits.asMap().entrySet()) {
                encoder.writeString(entry.getKey());
                writeImplicitList(encoder, entry.getValue());
            }
        }

        private void writeImplicitList(Encoder encoder, Collection<ImplicitInputRecord<?, ?>> implicits) throws Exception {
            encoder.writeSmallInt(implicits.size());
            for (ImplicitInputRecord<?, ?> implicit : implicits) {
                writeAny(encoder, implicit.getInput());
                writeAny(encoder, implicit.getOutput());
            }
        }

        private void writeAny(Encoder encoder, Object any) throws Exception {
            anySerializer.write(encoder, any);
        }
    }

    private static class DefaultImplicitInputRegistrar implements ImplicitInputRecorder {
        final Multimap<String, ImplicitInputRecord<?, ?>> implicits = HashMultimap.create();

        @Override
        public <IN, OUT> void register(String serviceName, ImplicitInputRecord<IN, OUT> input) {
            implicits.put(serviceName, input);
        }
    }

    private static class AnySerializer implements Serializer<Object> {
        private static final BaseSerializerFactory SERIALIZER_FACTORY = new BaseSerializerFactory();

        private static final Class<?>[] USUAL_TYPES = new Class<?>[] {
            String.class,
            Boolean.class,
            Long.class,
            File.class,
            byte[].class,
            HashCode.class,
            Throwable.class
        };

        @Override
        public Object read(Decoder decoder) throws Exception {
            int index = decoder.readSmallInt();
            if (index == -1) {
                return null;
            }
            Class<?> clazz;
            if (index == -2) {
                String typeName = decoder.readString();
                clazz = Class.forName(typeName);
            } else {
                clazz = USUAL_TYPES[index];
            }

            return SERIALIZER_FACTORY.getSerializerFor(clazz).read(decoder);
        }

        @Override
        public void write(Encoder encoder, Object value) throws Exception {
            if (value == null) {
                encoder.writeSmallInt(-1);
                return;
            }
            Class<?> anyType = value.getClass();
            Serializer<Object> serializer = Cast.uncheckedCast(SERIALIZER_FACTORY.getSerializerFor(anyType));
            for (int i = 0; i < USUAL_TYPES.length; i++) {
                if (USUAL_TYPES[i].equals(anyType)) {
                    encoder.writeSmallInt(i);
                    serializer.write(encoder, value);
                    return;
                }
            }
            encoder.writeSmallInt(-2);
            encoder.writeString(anyType.getName());
            serializer.write(encoder, value);
        }
    }
}
