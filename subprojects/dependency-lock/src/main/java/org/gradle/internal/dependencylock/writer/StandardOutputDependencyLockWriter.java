/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.dependencylock.writer;

import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.model.DependencyVersion;
import org.gradle.internal.dependencylock.model.GroupAndName;

import java.util.LinkedHashMap;
import java.util.Map;

public class StandardOutputDependencyLockWriter implements DependencyLockWriter {

    @Override
    public void write(DependencyLock dependencyLock) {
        System.out.println("");

        for (Map.Entry<String, LinkedHashMap<GroupAndName, DependencyVersion>> mapping : dependencyLock.getMapping().entrySet()) {
            System.out.println(mapping.getKey());

            for (Map.Entry<GroupAndName, DependencyVersion> dep : mapping.getValue().entrySet()) {
                String groupAndName = dep.getKey().toString();
                System.out.println("+--- " +  groupAndName + ":" + dep.getValue().getDeclaredVersion() + " -> " + dep.getValue().getResolvedVersion());
            }

            System.out.println("");
        }
    }
}
