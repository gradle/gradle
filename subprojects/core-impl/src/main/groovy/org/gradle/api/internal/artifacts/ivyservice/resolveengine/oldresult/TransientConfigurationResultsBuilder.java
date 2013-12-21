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

import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifierSerializer;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.util.Clock;

import java.io.IOException;

import static com.google.common.collect.Sets.newHashSet;
import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

//TODO SF unit coverage
public class TransientConfigurationResultsBuilder {

    private final static Logger LOG = Logging.getLogger(TransientConfigurationResultsBuilder.class);

    private static final byte NEW_DEP = 1;
    private static final byte ROOT = 2;
    private static final byte FIRST_LVL = 3;
    private static final byte PARENT_CHILD = 4;
    private static final byte PARENT_ARTIFACT = 5;

    private final Object lock = new Object();

    private BinaryStore binaryStore;
    private Store<TransientConfigurationResults> cache;
    private final ResolvedConfigurationIdentifierSerializer resolvedConfigurationIdentifierSerializer = new ResolvedConfigurationIdentifierSerializer();
    private BinaryStore.BinaryData binaryData;

    public TransientConfigurationResultsBuilder(BinaryStore binaryStore, Store<TransientConfigurationResults> cache) {
        this.binaryStore = binaryStore;
        this.cache = cache;
    }

    private void writeId(final byte type, final ResolvedConfigurationIdentifier... ids) {
        binaryStore.write(new BinaryStore.WriteAction() {
            public void write(Encoder encoder) throws IOException {
                encoder.writeByte(type);
                for (ResolvedConfigurationIdentifier id : ids) {
                    resolvedConfigurationIdentifierSerializer.write(encoder, id);
                }
            }
        });
    }

    public void resolvedDependency(ResolvedConfigurationIdentifier id) {
        writeId(NEW_DEP, id);
    }

    public void done(ResolvedConfigurationIdentifier id) {
        writeId(ROOT, id);
        LOG.debug("Flushing resolved configuration data in {}. Wrote root {}.", binaryStore, id);
        binaryData = binaryStore.done();
    }

    public void firstLevelDependency(ResolvedConfigurationIdentifier id) {
        writeId(FIRST_LVL, id);
    }

    public void parentChildMapping(ResolvedConfigurationIdentifier parent, ResolvedConfigurationIdentifier child) {
        writeId(PARENT_CHILD, parent, child);
    }

    public void parentSpecificArtifact(ResolvedConfigurationIdentifier child, ResolvedConfigurationIdentifier parent, final long artifactId) {
        writeId(PARENT_ARTIFACT, child, parent);
        binaryStore.write(new BinaryStore.WriteAction() {
            public void write(Encoder encoder) throws IOException {
                encoder.writeLong(artifactId);
            }
        });
    }

    public TransientConfigurationResults load(final ResolvedContentsMapping mapping) {
        synchronized (lock) {
            return cache.load(new Factory<TransientConfigurationResults>() {
                public TransientConfigurationResults create() {
                    try {
                        return binaryData.read(new BinaryStore.ReadAction<TransientConfigurationResults>() {
                            public TransientConfigurationResults read(Decoder decoder) throws IOException {
                                return deserialize(decoder, mapping);
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

    private TransientConfigurationResults deserialize(Decoder decoder, ResolvedContentsMapping mapping) {
        Clock clock = new Clock();
        DefaultTransientConfigurationResults results = new DefaultTransientConfigurationResults();
        int valuesRead = 0;
        byte type = -1;
        try {
            while (true) {
                type = decoder.readByte();
                ResolvedConfigurationIdentifier id;
                valuesRead++;
                switch (type) {
                    case NEW_DEP:
                        id = resolvedConfigurationIdentifierSerializer.read(decoder);
                        results.allDependencies.put(id, new DefaultResolvedDependency(id.getId(), id.getConfiguration()));
                        break;
                    case ROOT:
                        id = resolvedConfigurationIdentifierSerializer.read(decoder);
                        results.root = results.allDependencies.get(id);
                        if (results.root == null) {
                            throw new IllegalStateException(String.format("Unexpected root id %s. Seen ids: %s", id, results.allDependencies.keySet()));
                        }
                        //root should be the last
                        LOG.debug("Loaded resolved configuration results ({}) from {}", clock.getTime(), binaryStore);
                        return results;
                    case FIRST_LVL:
                        id = resolvedConfigurationIdentifierSerializer.read(decoder);
                        DefaultResolvedDependency dependency = results.allDependencies.get(id);
                        if (dependency == null) {
                            throw new IllegalStateException(String.format("Unexpected first level id %s. Seen ids: %s", id, results.allDependencies.keySet()));
                        }
                        results.firstLevelDependencies.put(mapping.getModuleDependency(id), dependency);
                        break;
                    case PARENT_CHILD:
                        ResolvedConfigurationIdentifier parentId = resolvedConfigurationIdentifierSerializer.read(decoder);
                        ResolvedConfigurationIdentifier childId = resolvedConfigurationIdentifierSerializer.read(decoder);
                        DefaultResolvedDependency parent = results.allDependencies.get(parentId);
                        DefaultResolvedDependency child = results.allDependencies.get(childId);
                        if (parent == null) {
                            throw new IllegalStateException(String.format("Unexpected parent dependency id %s. Seen ids: %s", parentId, results.allDependencies.keySet()));
                        }
                        if (child == null) {
                            throw new IllegalStateException(String.format("Unexpected child dependency id %s. Seen ids: %s", childId, results.allDependencies.keySet()));
                        }
                        parent.addChild(child);
                        break;
                    case PARENT_ARTIFACT:
                        ResolvedConfigurationIdentifier artifactParentId = resolvedConfigurationIdentifierSerializer.read(decoder);
                        ResolvedConfigurationIdentifier artifactChildId = resolvedConfigurationIdentifierSerializer.read(decoder);
                        DefaultResolvedDependency artifactParent = results.allDependencies.get(artifactParentId);
                        DefaultResolvedDependency artifactChild = results.allDependencies.get(artifactChildId);
                        if (artifactParent == null) {
                            throw new IllegalStateException(String.format("Unexpected parent dependency id %s. Seen ids: %s", artifactParentId, results.allDependencies.keySet()));
                        }
                        if (artifactChild == null) {
                            throw new IllegalStateException(String.format("Unexpected child dependency id %s. Seen ids: %s", artifactChildId, results.allDependencies.keySet()));
                        }
                        artifactParent.addParentSpecificArtifacts(artifactChild, newHashSet(mapping.getArtifact(decoder.readLong())));
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
