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
import org.gradle.messaging.serialize.FlushableEncoder;
import org.gradle.messaging.serialize.InputStreamBackedDecoder;
import org.gradle.messaging.serialize.OutputStreamBackedEncoder;
import org.gradle.util.Clock;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static com.google.common.collect.Sets.newHashSet;
import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class TransientResultsStore {

    private final static Logger LOG = Logging.getLogger(TransientResultsStore.class);

    private static final byte NEW_DEP = 1;
    private static final byte ROOT = 2;
    private static final byte FIRST_LVL = 3;
    private static final byte PARENT_CHILD = 4;
    private static final byte PARENT_ARTIFACT = 5;

    private final Object lock = new Object();
    private final int offset;

    private FlushableEncoder encoder;
    private BinaryStore binaryStore;
    private Store<TransientConfigurationResults> cache;
    private final ResolvedConfigurationIdentifierSerializer resolvedConfigurationIdentifierSerializer = new ResolvedConfigurationIdentifierSerializer();

    public TransientResultsStore(BinaryStore binaryStore, Store<TransientConfigurationResults> cache) {
        this.binaryStore = binaryStore;
        this.cache = cache;
        DataOutputStream output = binaryStore.getOutput();
        this.offset = output.size();
        encoder = new OutputStreamBackedEncoder(output);
    }

    private void writeId(byte type, ResolvedConfigurationIdentifier... ids) {
        try {
            encoder.writeByte(type);
            for (ResolvedConfigurationIdentifier id : ids) {
                resolvedConfigurationIdentifierSerializer.write(encoder, id);
            }
        } catch (Exception e) {
            throw throwAsUncheckedException(e);
        }
    }

    public void resolvedDependency(ResolvedConfigurationIdentifier id) {
        writeId(NEW_DEP, id);
    }

    public void done(ResolvedConfigurationIdentifier id) {
        writeId(ROOT, id);
        LOG.debug("Flushing results stream (offset: {}) {}. Wrote root {}.", offset, binaryStore, id);
        try {
            encoder.flush();
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        } finally {
            encoder = null;
        }
    }

    public void firstLevelDependency(ResolvedConfigurationIdentifier id) {
        writeId(FIRST_LVL, id);
    }

    public void parentChildMapping(ResolvedConfigurationIdentifier parent, ResolvedConfigurationIdentifier child) {
        writeId(PARENT_CHILD, parent, child);
    }

    public void parentSpecificArtifact(ResolvedConfigurationIdentifier child, ResolvedConfigurationIdentifier parent, long artifactId) {
        writeId(PARENT_ARTIFACT, child, parent);
        try {
            encoder.writeLong(artifactId);
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
    }

    public TransientConfigurationResults load(final ResolvedContentsMapping mapping) {
        synchronized (lock) {
            return cache.load(new Factory<TransientConfigurationResults>() {
                public TransientConfigurationResults create() {
                    return deserialize(mapping, offset);
                }
            });
        }
    }

    private TransientConfigurationResults deserialize(ResolvedContentsMapping mapping, int offset) {
        Clock clock = new Clock();
        DefaultTransientConfigurationResults results = new DefaultTransientConfigurationResults();
        int valuesRead = 0;
        byte type = -1;
        try {
            DataInputStream input = binaryStore.getInput();
            try {
                input.skipBytes(offset);
                InputStreamBackedDecoder decoder = new InputStreamBackedDecoder(input);
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
                            //root should be the last
                            LOG.debug("Loaded resolved configuration results ({}) from {}", clock.getTime(), binaryStore);
                            return results;
                        case FIRST_LVL:
                            id = resolvedConfigurationIdentifierSerializer.read(decoder);
                            results.firstLevelDependencies.put(mapping.getModuleDependency(id), results.allDependencies.get(id));
                            break;
                        case PARENT_CHILD:
                            DefaultResolvedDependency parent = results.allDependencies.get(resolvedConfigurationIdentifierSerializer.read(decoder));
                            DefaultResolvedDependency child = results.allDependencies.get(resolvedConfigurationIdentifierSerializer.read(decoder));
                            parent.addChild(child);
                            break;
                        case PARENT_ARTIFACT:
                            DefaultResolvedDependency c = results.allDependencies.get(resolvedConfigurationIdentifierSerializer.read(decoder));
                            DefaultResolvedDependency p = results.allDependencies.get(resolvedConfigurationIdentifierSerializer.read(decoder));
                            c.addParentSpecificArtifacts(p, newHashSet(mapping.getArtifact(decoder.readLong())));
                            break;
                    }
                }
            } finally {
                input.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Problems loading the resolved configuration (" + clock.getTime() + ") from " + binaryStore.diagnose()
                    + ". Read " + valuesRead + " values, last was: " + type, e);
        }
    }
}
