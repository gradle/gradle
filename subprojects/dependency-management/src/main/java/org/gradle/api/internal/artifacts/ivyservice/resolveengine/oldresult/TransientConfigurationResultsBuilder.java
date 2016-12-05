/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.DependencyGraphNodeResult;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * Serializes the transient parts of the resolved configuration results.
 */
public class TransientConfigurationResultsBuilder {

    private final static Logger LOG = Logging.getLogger(TransientConfigurationResultsBuilder.class);

    private static final byte NEW_DEP = 1;
    private static final byte ROOT = 2;
    private static final byte FIRST_LVL = 3;
    private static final byte PARENT_CHILD = 4;

    private final Object lock = new Object();

    private BinaryStore binaryStore;
    private Store<TransientConfigurationResults> cache;
    private final ResolvedConfigurationIdentifierSerializer resolvedConfigurationIdentifierSerializer = new ResolvedConfigurationIdentifierSerializer();
    private BinaryStore.BinaryData binaryData;

    public TransientConfigurationResultsBuilder(BinaryStore binaryStore, Store<TransientConfigurationResults> cache) {
        this.binaryStore = binaryStore;
        this.cache = cache;
    }

    public void resolvedDependency(final Long id, final ResolvedConfigurationIdentifier details) {
        binaryStore.write(new BinaryStore.WriteAction() {
            @Override
            public void write(Encoder encoder) throws IOException {
                encoder.writeByte(NEW_DEP);
                encoder.writeSmallLong(id);
                resolvedConfigurationIdentifierSerializer.write(encoder, details);
            }
        });
    }

    public void done(final Long id) {
        binaryStore.write(new BinaryStore.WriteAction() {
            @Override
            public void write(Encoder encoder) throws IOException {
                encoder.writeByte(ROOT);
                encoder.writeSmallLong(id);
            }
        });
        LOG.debug("Flushing resolved configuration data in {}. Wrote root {}.", binaryStore, id);
        binaryData = binaryStore.done();
    }

    public void firstLevelDependency(final Long id) {
        binaryStore.write(new BinaryStore.WriteAction() {
            @Override
            public void write(Encoder encoder) throws IOException {
                encoder.writeByte(FIRST_LVL);
                encoder.writeSmallLong(id);
            }
        });
    }

    public void parentChildMapping(final Long parent, final Long child, final long artifactId) {
        binaryStore.write(new BinaryStore.WriteAction() {
            public void write(Encoder encoder) throws IOException {
                encoder.writeByte(PARENT_CHILD);
                encoder.writeSmallLong(parent);
                encoder.writeSmallLong(child);
                encoder.writeSmallLong(artifactId);
            }
        });
    }

    public TransientConfigurationResults load(final ResolvedGraphResults graphResults, final SelectedArtifactResults artifactResults) {
        synchronized (lock) {
            return cache.load(new Factory<TransientConfigurationResults>() {
                public TransientConfigurationResults create() {
                    try {
                        return binaryData.read(new BinaryStore.ReadAction<TransientConfigurationResults>() {
                            public TransientConfigurationResults read(Decoder decoder) throws IOException {
                                return deserialize(decoder, graphResults, artifactResults);
                            }
                        });
                    } finally {
                        try {
                            binaryData.close();
                        } catch (IOException e) {
                            throw throwAsUncheckedException(e);
                        }
                    }
                }
            });
        }
    }

    private TransientConfigurationResults deserialize(Decoder decoder, ResolvedGraphResults graphResults, SelectedArtifactResults artifactResults) {
        Timer clock = Timers.startTimer();
        Map<Long, DefaultResolvedDependency> allDependencies = new HashMap<Long, DefaultResolvedDependency>();
        Map<ModuleDependency, DependencyGraphNodeResult> firstLevelDependencies = new LinkedHashMap<ModuleDependency, DependencyGraphNodeResult>();
        DependencyGraphNodeResult root;
        int valuesRead = 0;
        byte type = -1;
        try {
            while (true) {
                type = decoder.readByte();
                long id;
                valuesRead++;
                switch (type) {
                    case NEW_DEP:
                        id = decoder.readSmallLong();
                        ResolvedConfigurationIdentifier details = resolvedConfigurationIdentifierSerializer.read(decoder);
                        allDependencies.put(id, new DefaultResolvedDependency(id, details));
                        break;
                    case ROOT:
                        id = decoder.readSmallLong();
                        root = allDependencies.get(id);
                        if (root == null) {
                            throw new IllegalStateException(String.format("Unexpected root id %s. Seen ids: %s", id, allDependencies.keySet()));
                        }
                        //root should be the last entry
                        LOG.debug("Loaded resolved configuration results ({}) from {}", clock.getElapsed(), binaryStore);
                        return new DefaultTransientConfigurationResults(root, firstLevelDependencies);
                    case FIRST_LVL:
                        id = decoder.readSmallLong();
                        DefaultResolvedDependency dependency = allDependencies.get(id);
                        if (dependency == null) {
                            throw new IllegalStateException(String.format("Unexpected first level id %s. Seen ids: %s", id, allDependencies.keySet()));
                        }
                        firstLevelDependencies.put(graphResults.getModuleDependency(id), dependency);
                        break;
                    case PARENT_CHILD:
                        long parentId = decoder.readSmallLong();
                        long childId = decoder.readSmallLong();
                        DefaultResolvedDependency parent = allDependencies.get(parentId);
                        DefaultResolvedDependency child = allDependencies.get(childId);
                        if (parent == null) {
                            throw new IllegalStateException(String.format("Unexpected parent dependency id %s. Seen ids: %s", parentId, allDependencies.keySet()));
                        }
                        if (child == null) {
                            throw new IllegalStateException(String.format("Unexpected child dependency id %s. Seen ids: %s", childId, allDependencies.keySet()));
                        }
                        parent.addChild(child);
                        child.addParentSpecificArtifacts(parent, artifactResults.getArtifacts(decoder.readSmallLong()));
                        break;
                    default:
                        throw new IOException("Unknown value type read from stream: " + type);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Problems loading the resolved configuration. Read " + valuesRead + " values, last was: " + type, e);
        }
    }
}
