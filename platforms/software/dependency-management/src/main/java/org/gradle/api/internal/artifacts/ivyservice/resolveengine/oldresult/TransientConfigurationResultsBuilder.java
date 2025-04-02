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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import java.io.IOException;
import java.util.HashMap;
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

    private final BinaryStore binaryStore;
    private final Store<TransientConfigurationResults> cache;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ResolutionHost resolutionHost;
    private final ModuleVersionIdentifierSerializer moduleVersionIdSerializer;
    private BinaryStore.BinaryData binaryData;

    public TransientConfigurationResultsBuilder(
        BinaryStore binaryStore,
        Store<TransientConfigurationResults> cache,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        BuildOperationExecutor buildOperationExecutor,
        ResolutionHost resolutionHost
    ) {
        this.moduleVersionIdSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        this.binaryStore = binaryStore;
        this.cache = cache;
        this.buildOperationExecutor = buildOperationExecutor;
        this.resolutionHost = resolutionHost;
    }

    public void resolvedDependency(final Long id, ModuleVersionIdentifier moduleVersionId, String variantName) {
        binaryStore.write(encoder -> {
            encoder.writeByte(NODE);
            encoder.writeSmallLong(id);
            moduleVersionIdSerializer.write(encoder, moduleVersionId);
            encoder.writeString(variantName);
        });
    }

    public void done(final Long id) {
        binaryStore.write(encoder -> {
            encoder.writeByte(ROOT);
            encoder.writeSmallLong(id);
        });
        LOG.debug("Flushing resolved configuration data in {}. Wrote root {}.", binaryStore, id);
        binaryData = binaryStore.done();
    }

    public void firstLevelDependency(final Long id) {
        binaryStore.write(encoder -> {
            encoder.writeByte(FIRST_LEVEL);
            encoder.writeSmallLong(id);
        });
    }

    public void parentChildMapping(final Long parent, final Long child, final int artifactId) {
        binaryStore.write(encoder -> {
            encoder.writeByte(EDGE);
            encoder.writeSmallLong(parent);
            encoder.writeSmallLong(child);
            encoder.writeSmallInt(artifactId);
        });
    }

    public void nodeArtifacts(final Long node, final int artifactId) {
        binaryStore.write(encoder -> {
            encoder.writeByte(NODE_ARTIFACTS);
            encoder.writeSmallLong(node);
            encoder.writeSmallInt(artifactId);
        });
    }

    public TransientConfigurationResults load(final SelectedArtifactResults artifactResults) {
        synchronized (lock) {
            return cache.load(() -> {
                try {
                    return binaryData.read(decoder -> deserialize(decoder, artifactResults, buildOperationExecutor, resolutionHost));
                } finally {
                    try {
                        binaryData.close();
                    } catch (IOException e) {
                        throw throwAsUncheckedException(e);
                    }
                }
            });
        }
    }

    private TransientConfigurationResults deserialize(
        Decoder decoder,
        SelectedArtifactResults artifactResults,
        BuildOperationExecutor buildOperationProcessor,
        ResolutionHost resolutionHost
    ) {
        Timer clock = Time.startTimer();
        Map<Long, DefaultResolvedDependency> allDependencies = new HashMap<>();
        ImmutableSet.Builder<ResolvedDependency> firstLevelDependencies = ImmutableSet.builder();
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
                        ModuleVersionIdentifier moduleVersionId = moduleVersionIdSerializer.read(decoder);
                        String variantName = decoder.readString();
                        allDependencies.put(id, new DefaultResolvedDependency(variantName, moduleVersionId, buildOperationProcessor, resolutionHost));
                        break;
                    case ROOT:
                        id = decoder.readSmallLong();
                        ResolvedDependency root = allDependencies.get(id);
                        if (root == null) {
                            throw new IllegalStateException(String.format("Unexpected root id %s. Seen ids: %s", id, allDependencies.keySet()));
                        }
                        //root should be the last entry
                        LOG.debug("Loaded resolved configuration results ({}) from {}", clock.getElapsed(), binaryStore);
                        return new DefaultTransientConfigurationResults(root, firstLevelDependencies.build());
                    case FIRST_LEVEL:
                        id = decoder.readSmallLong();
                        DefaultResolvedDependency dependency = allDependencies.get(id);
                        if (dependency == null) {
                            throw new IllegalStateException(String.format("Unexpected first level id %s. Seen ids: %s", id, allDependencies.keySet()));
                        }
                        firstLevelDependencies.add(dependency);
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
