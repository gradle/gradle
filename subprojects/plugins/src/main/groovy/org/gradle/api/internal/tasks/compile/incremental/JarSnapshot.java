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

package org.gradle.api.internal.tasks.compile.incremental;

import java.io.Serializable;
import java.util.*;

class JarSnapshot implements Serializable {

    final Map<String, ClassSnapshot> classSnapshots;

    JarSnapshot(Map<String, ClassSnapshot> classSnapshots) {
        this.classSnapshots = classSnapshots;
    }

    JarDependentsDelta getDependentsDelta(JarSnapshot other) {
        final List<String> allDependents = new LinkedList<String>();
        for (String thisCls : classSnapshots.keySet()) {
            ClassSnapshot otherCls = other.classSnapshots.get(thisCls);
            if (otherCls == null || !Arrays.equals(otherCls.hash, classSnapshots.get(thisCls).hash)) {
                Collection<String> dependents = classSnapshots.get(thisCls).dependentClasses;
                if (dependents == null) { //TODO SF don't model as null
                    return new JarDependentsDelta() {
                        public Collection<String> getDependentClasses() {
                            return null;
                        }
                    };
                }
                allDependents.addAll(dependents);
            }
        }
        return new JarDependentsDelta() {
            public Collection<String> getDependentClasses() {
                return allDependents;
            }
        };
    }
}