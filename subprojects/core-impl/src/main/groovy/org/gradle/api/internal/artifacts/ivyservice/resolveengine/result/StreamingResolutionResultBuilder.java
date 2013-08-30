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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.EncodedWriteAction;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.FlushableEncoder;
import org.gradle.messaging.serialize.InputStreamBackedDecoder;
import org.gradle.util.Clock;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;

/**
 * by Szczepan Faber on 7/28/13
 */
public class StreamingResolutionResultBuilder implements ResolutionResultBuilder {

    private final static byte ROOT = 1;
    private final static byte MODULE = 2;
    private final static byte DEPENDENCY = 3;
    private final static byte DONE = 4;

    private final Map<ModuleVersionSelector, ModuleVersionResolveException> failures = new HashMap<ModuleVersionSelector, ModuleVersionResolveException>();
    private final BinaryStore store;
    private final ModuleVersionIdentifierSerializer moduleVersionIdentifierSerializer = new ModuleVersionIdentifierSerializer();
    private final ModuleVersionSelectionSerializer moduleVersionSelectionSerializer = new ModuleVersionSelectionSerializer();
    private Store<ResolvedModuleVersionResult> cache;
    private final InternalDependencyResultSerializer internalDependencyResultSerializer = new InternalDependencyResultSerializer();

    public StreamingResolutionResultBuilder(BinaryStore store, Store<ResolvedModuleVersionResult> cache) {
        this.store = store;
        this.cache = cache;
    }

    public ResolutionResult complete() {
        store.write(new EncodedWriteAction() {
            public void write(FlushableEncoder encoder) throws IOException {
                encoder.writeByte(DONE);
            }
        });
        BinaryStore.BinaryData data = store.done();
        RootFactory rootSource = new RootFactory(data, failures, cache);
        return new DefaultResolutionResult(rootSource);
    }

    public ResolutionResultBuilder start(final ModuleVersionIdentifier root) {
        store.write(new EncodedWriteAction() {
            public void write(FlushableEncoder encoder) throws IOException {
                encoder.writeByte(ROOT);
                moduleVersionIdentifierSerializer.write(encoder, root);
            }
        });
        return this;
    }

    Set<ModuleVersionIdentifier> visitedModules = new HashSet<ModuleVersionIdentifier>();

    public void resolvedModuleVersion(final ModuleVersionSelection moduleVersion) {
        if (visitedModules.add(moduleVersion.getSelectedId())) {
            store.write(new EncodedWriteAction() {
                public void write(FlushableEncoder encoder) throws IOException {
                    encoder.writeByte(MODULE);
                    moduleVersionSelectionSerializer.write(encoder, moduleVersion);
                }
            });
        }
    }

    public void resolvedConfiguration(final ModuleVersionIdentifier from, final Collection<? extends InternalDependencyResult> dependencies) {
        if (!dependencies.isEmpty()) {
            store.write(new EncodedWriteAction() {
                public void write(FlushableEncoder encoder) throws IOException {
                    encoder.writeByte(DEPENDENCY);
                    moduleVersionIdentifierSerializer.write(encoder, from);
                    encoder.writeInt(dependencies.size());
                    for (InternalDependencyResult dependency : dependencies) {
                        internalDependencyResultSerializer.write(encoder, dependency);
                        if (dependency.getFailure() != null) {
                            //by keying the failures only be 'requested' we lose some precision
                            //at edge case we'll lose info about a different exception if we have different failure for the same requested version
                            failures.put(dependency.getRequested(), dependency.getFailure());
                        }
                    }
                }
            });
        }
    }

    private static class RootFactory implements Factory<ResolvedModuleVersionResult> {

        private final static Logger LOG = Logging.getLogger(RootFactory.class);
        private final ModuleVersionSelectionSerializer moduleVersionSelectionSerializer = new ModuleVersionSelectionSerializer();

        private BinaryStore.BinaryData data;
        private final Map<ModuleVersionSelector, ModuleVersionResolveException> failures;
        private Store<ResolvedModuleVersionResult> cache;
        private final Object lock = new Object();
        private final ModuleVersionIdentifierSerializer moduleVersionIdentifierSerializer = new ModuleVersionIdentifierSerializer();
        private final InternalDependencyResultSerializer internalDependencyResultSerializer = new InternalDependencyResultSerializer();

        public RootFactory(BinaryStore.BinaryData data, Map<ModuleVersionSelector, ModuleVersionResolveException> failures,
                           Store<ResolvedModuleVersionResult> cache) {
            this.data = data;
            this.failures = failures;
            this.cache = cache;
        }

        public ResolvedModuleVersionResult create() {
            synchronized (lock) {
                return cache.load(new Factory<ResolvedModuleVersionResult>() {
                    public ResolvedModuleVersionResult create() {
                        try {
                            return data.read(new BinaryStore.ReadAction<ResolvedModuleVersionResult>() {
                                public ResolvedModuleVersionResult read(DataInputStream input) throws IOException {
                                    return deserialize(input);
                                }
                            });
                        } finally {
                            data.done();
                        }
                    }
                });
            }
        }

        private ResolvedModuleVersionResult deserialize(DataInputStream input) {
            int valuesRead = 0;
            byte type = -1;
            Clock clock = new Clock();
            try {
                DefaultResolutionResultBuilder builder = new DefaultResolutionResultBuilder();
                Decoder decoder = new InputStreamBackedDecoder(input);
                while (true) {
                    type = decoder.readByte();
                    valuesRead++;
                    switch (type) {
                        case ROOT:
                            ModuleVersionIdentifier id = moduleVersionIdentifierSerializer.read(decoder);
                            builder.start(id);
                            break;
                        case MODULE:
                            ModuleVersionSelection sel = moduleVersionSelectionSerializer.read(decoder);
                            builder.resolvedModuleVersion(sel);
                            break;
                        case DEPENDENCY:
                            id = moduleVersionIdentifierSerializer.read(decoder);
                            int size = decoder.readInt();
                            List<InternalDependencyResult> deps = new LinkedList<InternalDependencyResult>();
                            for (int i = 0; i < size; i++) {
                                deps.add(internalDependencyResultSerializer.read(decoder, failures));
                            }
                            builder.resolvedConfiguration(id, deps);
                            break;
                        case DONE:
                            ResolvedModuleVersionResult root = builder.complete().getRoot();
                            LOG.debug("Loaded resolution results ({}) from {}", clock.getTime(), data);
                            return root;
                        default:
                            throw new IOException("Unknown value type read from stream: " + type);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Problems loading the resolution results (" + clock.getTime() + "). "
                        + "Read " + valuesRead + " values, last was: " + type, e);
            }
        }
    }
}
