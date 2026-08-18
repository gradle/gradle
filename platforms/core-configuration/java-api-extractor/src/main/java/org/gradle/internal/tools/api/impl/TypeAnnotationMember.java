/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.tools.api.impl;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.TypePath;

public class TypeAnnotationMember extends AnnotationMember {
    private final int typeRef;
    @Nullable
    private final TypePath typePath;

    public TypeAnnotationMember(String desc, boolean visible, int typeRef, @Nullable TypePath typePath) {
        super(desc, visible);
        this.typeRef = typeRef;
        this.typePath = typePath;
    }

    public int getTypeRef() {
        return typeRef;
    }

    /**
     * The path to the annotated type, or {@code null} when the annotated type is the whole type.
     */
    @Nullable
    public TypePath getTypePath() {
        return typePath;
    }

    @Override
    protected int kindRank() {
        return 2;
    }

    @Override
    public int compareTo(AnnotationMember o) {
        if (!(o instanceof TypeAnnotationMember)) {
            // The rank of the kinds decides, and it never leaves them equal
            return super.compare(o).result();
        }
        TypeAnnotationMember other = (TypeAnnotationMember) o;
        return super.compare(o)
            // The same annotation can appear on several types of the same member,
            // so the target must be part of the identity
            .compare(typeRef, other.typeRef)
            .compare(typePathString(), other.typePathString())
            .result();
    }

    private String typePathString() {
        return typePath == null ? "" : typePath.toString();
    }
}
