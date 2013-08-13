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
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.internal.Factory;

import java.io.*;
import java.util.*;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * by Szczepan Faber on 7/28/13
 */
public class StreamingResolutionResultBuilder implements ResolvedConfigurationListener {

    private final static short ROOT = 1;
    private final static short MODULE = 2;
    private final static short DEPENDENCY = 3;
    private final static short DONE = 4;

    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private final DataOutputStream output = new DataOutputStream(stream);
    private final Map<ModuleVersionSelector, ModuleVersionResolveException> failures = new HashMap<ModuleVersionSelector, ModuleVersionResolveException>();

    public ResolutionResult complete() {
        try {
            output.writeShort(DONE);
            output.close();
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }

        RootFactory rootSource = new RootFactory(new ByteArrayInputStream(stream.toByteArray()), failures);
        return new DefaultResolutionResult(rootSource);
    }

    public ResolvedConfigurationListener start(ModuleVersionIdentifier root) {
        try {
            output.writeShort(ROOT);
            new ModuleVersionIdentifierSerializer().write((DataOutput) output, root);
            output.close();
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
        return this;
    }

    Set<ModuleVersionIdentifier> visitedModules = new HashSet<ModuleVersionIdentifier>();

    public void resolvedModuleVersion(ModuleVersionSelection moduleVersion) {
        if(visitedModules.add(moduleVersion.getSelectedId())) {
            try {
                output.writeShort(MODULE);
                new ModuleVersionSelectionSerializer().write((DataOutput) output, moduleVersion);
            } catch (IOException e) {
                throw throwAsUncheckedException(e);
            }
        }
    }

    public void resolvedConfiguration(ModuleVersionIdentifier from, Collection<? extends InternalDependencyResult> dependencies) {
        if (!dependencies.isEmpty()) {
            try {
                output.writeShort(DEPENDENCY);
                new ModuleVersionIdentifierSerializer().write((DataOutput) output, from);
                output.writeInt(dependencies.size());
                InternalDependencyResultSerializer s = new InternalDependencyResultSerializer();
                for (InternalDependencyResult dependency : dependencies) {
                    s.write(output, dependency);
                    if (dependency.getFailure() != null) {
                        //by keying the failures only be 'requested' we lose some precision
                        //at edge case we'll lose info about a different exception if we have different failure for the same requested version
                        failures.put(dependency.getRequested(), dependency.getFailure());
                    }
                }
            } catch (IOException e) {
                throw throwAsUncheckedException(e);
            }
        }
    }

    private static class RootFactory implements Factory<ResolvedModuleVersionResult> {

        private final DataInputStream input;
        private final Map<ModuleVersionSelector, ModuleVersionResolveException> failures;
        private boolean completed;

        public RootFactory(ByteArrayInputStream inputStream, Map<ModuleVersionSelector, ModuleVersionResolveException> failures) {
            this.failures = failures;
            input = new DataInputStream(inputStream);
        }

        public ResolvedModuleVersionResult create() {
            assert !completed;
            ResolutionResultBuilder builder = new ResolutionResultBuilder();
            int valuesRead = 0;
            short type = -1;
            try {
                while (true) {
                    ModuleVersionIdentifierSerializer s = new ModuleVersionIdentifierSerializer();
                    type = input.readShort();
                    valuesRead++;
                    switch (type) {
                        case ROOT:
                            ModuleVersionIdentifier id =  s.read((DataInput) input);
                            builder.start(id);
                            break;
                        case MODULE:
                            ModuleVersionSelection sel = new ModuleVersionSelectionSerializer().read((DataInput) input);
                            builder.resolvedModuleVersion(sel);
                            break;
                        case DEPENDENCY:
                            id = s.read((DataInput) input);
                            int size = input.readInt();
                            InternalDependencyResultSerializer depSerializer = new InternalDependencyResultSerializer();
                            List<InternalDependencyResult> deps = new LinkedList<InternalDependencyResult>();
                            for (int i=0; i<size; i++) {
                                deps.add(depSerializer.read(input, failures));
                            }
                            builder.resolvedConfiguration(id, deps);
                            break;
                        case DONE:
                            return builder.complete().getRoot();
                        default:
                            throw new IllegalArgumentException("Unknown value type: " + type);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Problems loading the resolution result (new model). Read: "
                        + valuesRead + " values, last: " + type, e);
            } finally {
                completed = true;
            }
        }
    }
}
