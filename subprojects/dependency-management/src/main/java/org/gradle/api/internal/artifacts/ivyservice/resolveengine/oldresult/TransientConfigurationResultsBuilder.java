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
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.internal.Factory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

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

    private static final byte NODE = 1;
    private static final byte ROOT = 2;
    private static final byte FIRST_LEVEL = 3;
    private static final byte EDGE = 4;
    private static final byte NODE_ARTIFACTS = 5;

    private final Object lock = new Object();

    private BinaryStore binaryStore;
    private Store<TransientConfigurationResults> cache;
    private final BuildOperationExecutor buildOperationProcessor;
    private final ResolvedConfigurationIdentifierSerializer resolvedConfigurationIdentifierSerializer;
    private BinaryStore.BinaryData binaryData;

    public TransientConfigurationResultsBuilder(BinaryStore binaryStore, Store<TransientConfigurationResults> cache, ImmutableModuleIdentifierFactory moduleIdentifierFactory, BuildOperationExecutor buildOperationProcessor) {
        this.resolvedConfigurationIdentifierSerializer = new ResolvedConfigurationIdentifierSerializer(moduleIdentifierFactory);
        this.binaryStore = binaryStore;
        this.cache = cache;
        this.buildOperationProcessor = buildOperationProcessor;
    }

    public void resolvedDependency(final Long id, final ResolvedConfigurationIdentifier details) {
        binaryStore.write(new BinaryStore.WriteAction() {
            @Override
            public void write(Encoder encoder) throws IOException {
                encoder.writeByte(NODE);
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
                encoder.writeByte(FIRST_LEVEL);
                encoder.writeSmallLong(id);
            }
        });
    }

    public void parentChildMapping(final Long parent, final Long child, final int artifactId) {
        binaryStore.write(new BinaryStore.WriteAction() {
            public void write(Encoder encoder) throws IOException {
                encoder.writeByte(EDGE);
                encoder.writeSmallLong(parent);
                encoder.writeSmallLong(child);
                encoder.writeSmallInt(artifactId);
            }
        });
    }

    public void nodeArtifacts(final Long node, final int artifactId) {
        binaryStore.write(new BinaryStore.WriteAction() {
            public void write(Encoder encoder) throws IOException {
                encoder.writeByte(NODE_ARTIFACTS);
                encoder.writeSmallLong(node);
                encoder.writeSmallInt(artifactId);
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
                                return deserialize(decoder, graphResults, artifactResults, buildOperationProcessor);
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

    private TransientConfigurationResults deserialize(Decoder decoder, ResolvedGraphResults graphResults, SelectedArtifactResults artifactResults, BuildOperationExecutor buildOperationProcessor) {
        Timer clock = Time.startTimer();
        Map<Long, DefaultResolvedDependency> allDependencies = new HashMap<Long, DefaultResolvedDependency>();
        Map<ModuleDependency, DependencyGraphNodeResult> firstLevelDependencies = new LinkedHashMap<ModuleDependency, DependencyGraphNodeResult>();
        DependencyGraphNodeResult root;
        int valuesRead = 0;
        byte type = -1;
        long id;
        ResolvedArtifactSet artifacts;
        try {
            while (true) {
                type = decoder.readByte();
                valuesRead++;
                switch (type) {
                    case NODE:
                        id = decoder.readSmallLong();
                        ResolvedConfigurationIdentifier details = resolvedConfigurationIdentifierSerializer.read(decoder);
                        allDependencies.put(id, new DefaultResolvedDependency(id, details, buildOperationProcessor));
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
                    case FIRST_LEVEL:
                        id = decoder.readSmallLong();
                        DefaultResolvedDependency dependency = allDependencies.get(id);
                        if (dependency == null) {
                            throw new IllegalStateException(String.format("Unexpected first level id %s. Seen ids: %s", id, allDependencies.keySet()));
                        }
                        firstLevelDependencies.put(graphResults.getModuleDependency(id), dependency);
                        break;
                    case EDGE:
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
                        artifacts = artifactResults.getArtifactsWithId(decoder.readSmallInt());
                        child.addParentSpecificArtifacts(parent, artifacts);
                        break;
                    case NODE_ARTIFACTS:
                        id = decoder.readSmallLong();
                        DefaultResolvedDependency node = allDependencies.get(id);
                        if (node == null) {
                            throw new IllegalStateException(String.format("Unexpected node id %s. Seen ids: %s", node, allDependencies.keySet()));
                        }
                        artifacts = artifactResults.getArtifactsWithId(decoder.readSmallInt());
                        node.addModuleArtifacts(artifacts);
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
