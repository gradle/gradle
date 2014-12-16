/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

/**
 * Identifies node in the classloader scope hierarchy
 */
class ScopeNodeIdentifier {

    private final IdGenerator<Long> generator;
    private final String node;

    ScopeNodeIdentifier(String node, IdGenerator<Long> generator) {
        this.node = node;
        this.generator = generator;
    }

    /**
     * creates new child node identifier
     */
    ScopeNodeIdentifier newChild() {
        return new ScopeNodeIdentifier(node + ":c" + generator.generateId(), new LongIdGenerator());
    }

    /**
     * local classloader id of this node
     */
    ClassLoaderId localId() {
        return ClassLoaderIds.scopeNode(node.concat("-local"));
    }

    /**
     * export classloader id of this node
     */
    ClassLoaderId exportId() {
        return ClassLoaderIds.scopeNode(node.concat("-export"));
    }
}
