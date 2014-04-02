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

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependents;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class JarSnapshot implements Serializable {

    final Map<String, ClassSnapshot> classSnapshots;

    JarSnapshot(Map<String, ClassSnapshot> classSnapshots) {
        this.classSnapshots = classSnapshots;
    }

    DependentsSet getDependentsDelta(JarSnapshot current) {
        final List<String> allDependents = new LinkedList<String>();
        for (String thisCls : classSnapshots.keySet()) {
            ClassSnapshot otherCls = current.classSnapshots.get(thisCls);
            //if class was removed from current snapshot or hash does not match
            if (otherCls == null || !Arrays.equals(otherCls.getHash(), classSnapshots.get(thisCls).getHash())) {
                DependentsSet dependents = classSnapshots.get(thisCls).getDependents();
                if (dependents.isDependencyToAll()) {
                    //one of the classes changed/removed in the jar is a 'dependencyToAll', let's return it, there is no point in further collection of dependents.
                    return dependents;
                }
                allDependents.addAll(dependents.getDependentClasses());
            }
        }
        return new ClassDependents(allDependents);
    }
}