/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.internal.classpath.TypeCollectingClasspathFileTransformer.TypeRegistry;
import org.gradle.internal.lazy.Lazy;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collections;
import java.util.Set;

public class ClassData {
    private final TypeRegistry typeRegistry;
    private final Lazy<ClassNode> lazyClassNode;

    ClassData(ClassReader reader, TypeRegistry typeRegistry) {
        lazyClassNode = Lazy.unsafe().of(() -> {
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);
            return classNode;
        });
        this.typeRegistry = typeRegistry;
    }


    public ClassNode readClassAsNode() {
        return lazyClassNode.get();
    }

    public Set<String> getSubtypes(String superType) {
        return typeRegistry.getSubTypes(superType);
    }

    public Set<String> getSuperTypes(String type) {
        return typeRegistry.getSuperTypes(type);
    }
}
