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
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JarSnapshot implements Serializable {

    private Map<String, byte[]> hashes;
    private ClassDependencyInfo info;

    public JarSnapshot(Map<String, byte[]> hashes, ClassDependencyInfo info) {
        this.hashes = hashes;
        this.info = info;
    }

    public DependentsSet getAllClasses() {
        final Set<String> result = new HashSet<String>();
        for (Map.Entry<String, byte[]> cls : hashes.entrySet()) {
            String className = cls.getKey();
            DependentsSet dependents = info.getRelevantDependents(className);
            if (dependents.isDependencyToAll()) {
                return dependents;
            }
            result.add(className);
        }
        return new DefaultDependentsSet(result);
    }

    public DependentsSet getAffectedClassesSince(JarSnapshot other) {
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
            //we ignore added since
        }
        return new DefaultDependentsSet(affected);
    }
}