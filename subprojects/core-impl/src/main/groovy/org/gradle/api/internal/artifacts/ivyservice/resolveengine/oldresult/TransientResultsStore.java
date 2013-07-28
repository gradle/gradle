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

import java.io.*;

import static com.google.common.collect.Sets.newHashSet;
import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

class TransientResultsStore {

    private static final short NEW_DEP = 1;
    private static final short ROOT = 2;
    private static final short FIRST_LVL = 3;
    private static final short PARENT_CHILD = 4;
    private static final short PARENT_ARTIFACT = 5;

    private final Object lock = new Object();
    private DefaultTransientConfigurationResults cache;

    private DataOutputStream output;
    private DataInputStream input;

    TransientResultsStore(BinaryStore store) {
        this.output = store.getOutput();
        this.input = store.getInput();
    }

    private void writeId(short type, ResolvedConfigurationIdentifier... ids) {
        try {
            output.writeShort(type);
            ResolvedConfigurationIdentifierSerializer s = new ResolvedConfigurationIdentifierSerializer();
            for (ResolvedConfigurationIdentifier id : ids) {
                s.write((DataOutput) output, id);
            }
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
    }

    public void resolvedDependency(ResolvedConfigurationIdentifier id) {
        writeId(NEW_DEP, id);
    }

    public void done(ResolvedConfigurationIdentifier id) {
        writeId(ROOT, id);
        try {
            output.close();
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
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
            output.writeLong(artifactId);
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
    }

    public TransientConfigurationResults load(ResolvedContentsMapping mapping) {
        synchronized (lock) {
            if (cache != null) {
                return cache;
            }
            cache = new DefaultTransientConfigurationResults();
            output = null;
            try {
                while (true) {
                    ResolvedConfigurationIdentifierSerializer s = new ResolvedConfigurationIdentifierSerializer();
                    short type = input.readShort();
                    ResolvedConfigurationIdentifier id;
                    switch (type) {
                        case 1:
                            id = s.read((DataInput) input);
                            cache.allDependencies.put(id, new DefaultResolvedDependency(id.getId(), id.getConfiguration()));
                            break;
                        case 2:
                            id = s.read((DataInput) input);
                            cache.root = cache.allDependencies.get(id);
                            //root should be the last
                            return cache;
                        case 3:
                            id = s.read((DataInput) input);
                            cache.firstLevelDependencies.put(mapping.getModuleDependency(id), cache.allDependencies.get(id));
                            break;
                        case 4:
                            DefaultResolvedDependency parent = cache.allDependencies.get(s.read((DataInput) input));
                            DefaultResolvedDependency child = cache.allDependencies.get(s.read((DataInput) input));
                            parent.addChild(child);
                            break;
                        case 5:
                            DefaultResolvedDependency c = cache.allDependencies.get(s.read((DataInput) input));
                            DefaultResolvedDependency p = cache.allDependencies.get(s.read((DataInput) input));
                            c.addParentSpecificArtifacts(p, newHashSet(mapping.getArtifact(input.readLong())));
                            break;
                    }
                }
            } catch (IOException e) {
                throw throwAsUncheckedException(e);
            }
        }
    }
}
