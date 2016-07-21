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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.tasks.compile.incremental.deps.AffectedClasses;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.DefaultDependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependencyToAll;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JarSnapshot {

    private final JarSnapshotData data;

    public JarSnapshot(JarSnapshotData data) {
        this.data = data;
    }

    public DependentsSet getAllClasses() {
        final Set<String> result = new HashSet<String>();
        for (Map.Entry<String, HashCode> cls : getHashes().entrySet()) {
            String className = cls.getKey();
            if (getAnalysis().isDependencyToAll(className)) {
                return new DependencyToAll();
            }
            result.add(className);
        }
        return new DefaultDependentsSet(result);
    }

    public AffectedClasses getAffectedClassesSince(JarSnapshot other) {
        DependentsSet affectedClasses = affectedSince(other);
        Set<String> addedClasses = addedSince(other);
        return new AffectedClasses(affectedClasses, addedClasses);
    }

    private DependentsSet affectedSince(JarSnapshot other) {
        final Set<String> affected = new HashSet<String>();
        for (Map.Entry<String, HashCode> otherClass : other.getHashes().entrySet()) {
            String otherClassName = otherClass.getKey();
            HashCode otherClassBytes = otherClass.getValue();
            HashCode thisClsBytes = getHashes().get(otherClassName);
            if (thisClsBytes == null || !thisClsBytes.equals(otherClassBytes)) {
                //removed since or changed since
                affected.add(otherClassName);
                DependentsSet dependents = other.getAnalysis().getRelevantDependents(otherClassName);
                if (dependents.isDependencyToAll()) {
                    return dependents;
                }
                affected.addAll(dependents.getDependentClasses());
            }
        }
        return new DefaultDependentsSet(affected);
    }

    private Set<String> addedSince(JarSnapshot other) {
        Set<String> addedClasses = new HashSet<String>(getClasses());
        addedClasses.removeAll(other.getClasses());
        return addedClasses;
    }

    public HashCode getHash() {
        return data.hash;
    }

    public Map<String, HashCode> getHashes() {
        return data.hashes;
    }

    public ClassSetAnalysis getAnalysis() {
        return new ClassSetAnalysis(data.data);
    }

    public Set<String> getClasses() {
        return data.hashes.keySet();
    }

    public JarSnapshotData getData() {
        return data;
    }
}
