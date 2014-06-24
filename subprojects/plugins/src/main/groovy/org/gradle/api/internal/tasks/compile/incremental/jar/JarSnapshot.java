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

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.deps.DefaultDependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependencyToAll;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JarSnapshot implements Serializable {

    //TODO SF refactor all persistent objects into data objects, otherwise any signature changes in the class leads to serializable problems
    //down the road we'll have hand crafted serialization

    final Map<String, byte[]> hashes;
    final ClassDependencyInfo info;

    private final byte[] hash;

    /**
     * @param hash of this jar
     * @param hashes hashes of all classes from the jar
     * @param info dependency info of classes in this jar
     */
    public JarSnapshot(byte[] hash, Map<String, byte[]> hashes, ClassDependencyInfo info) {
        assert hash != null;
        assert hashes != null;
        assert info != null;

        this.hash = hash;
        this.hashes = hashes;
        this.info = info;
    }

    public DependentsSet getAllClasses() {
        final Set<String> result = new HashSet<String>();
        for (Map.Entry<String, byte[]> cls : hashes.entrySet()) {
            String className = cls.getKey();
            if (info.isDependencyToAll(className)) {
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
        for (Map.Entry<String, byte[]> otherClass : other.hashes.entrySet()) {
            String otherClassName = otherClass.getKey();
            byte[] otherClassBytes = otherClass.getValue();
            byte[] thisClsBytes = hashes.get(otherClassName);
            if (thisClsBytes == null || !Arrays.equals(thisClsBytes, otherClassBytes)) {
                //removed since or changed since
                affected.add(otherClassName);
                DependentsSet dependents = other.info.getRelevantDependents(otherClassName);
                if (dependents.isDependencyToAll()) {
                    return dependents;
                }
                affected.addAll(dependents.getDependentClasses());
            }
        }
        return new DefaultDependentsSet(affected);
    }

    private Set<String> addedSince(JarSnapshot other) {
        Set<String> addedClasses = new HashSet<String>(hashes.keySet());
        addedClasses.removeAll(other.hashes.keySet());
        return addedClasses;
    }

    public byte[] getHash() {
        return hash;
    }

    public Set<String> getClasses() {
        return hashes.keySet();
    }
}