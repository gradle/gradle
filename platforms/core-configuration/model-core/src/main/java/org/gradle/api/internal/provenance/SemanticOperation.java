/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provenance;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Classification supplied at an accepted mutation boundary, containing no executable transforms or values.
 * An unclassified binding is never evidence of a non-self replacement or contributor authority.
 */
public final class SemanticOperation {
    public enum Kind { EXPLICIT_BINDING, CONVENTION_BINDING, UPDATE, CLEAR_EXPLICIT, CLEAR_CONVENTION, PROMOTE_CONVENTION, UNCLASSIFIED_BINDING }
    public enum Shape { MAP, FLAT_MAP, ZIP, APPEND, REMOVE }

    public static final SemanticOperation EXPLICIT_BINDING = new SemanticOperation(Kind.EXPLICIT_BINDING, Collections.emptyList(), "");
    public static final SemanticOperation CONVENTION_BINDING = new SemanticOperation(Kind.CONVENTION_BINDING, Collections.emptyList(), "");
    public static final SemanticOperation CLEAR_EXPLICIT = new SemanticOperation(Kind.CLEAR_EXPLICIT, Collections.emptyList(), "");
    public static final SemanticOperation CLEAR_CONVENTION = new SemanticOperation(Kind.CLEAR_CONVENTION, Collections.emptyList(), "");
    public static final SemanticOperation PROMOTE_CONVENTION = new SemanticOperation(Kind.PROMOTE_CONVENTION, Collections.emptyList(), "");

    private final Kind kind;
    private final List<Shape> shapes;
    private final String reason;

    private SemanticOperation(Kind kind, List<Shape> shapes, String reason) {
        this.kind = kind;
        this.shapes = shapes;
        this.reason = reason;
    }

    /** A compound shape still describes just one accepted mutation. Tags do not imply supported runtime classifiers. */
    public static SemanticOperation update(Shape... shapes) {
        if (shapes.length == 0) {
            throw new IllegalArgumentException("An update needs a structural shape.");
        }
        Shape[] copy = shapes.clone();
        for (Shape shape : copy) {
            Objects.requireNonNull(shape);
        }
        return new SemanticOperation(Kind.UPDATE, Collections.unmodifiableList(Arrays.asList(copy)), "");
    }

    public static SemanticOperation unclassifiedBinding(String reason) {
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("An unclassified binding needs a reason.");
        }
        return new SemanticOperation(Kind.UNCLASSIFIED_BINDING, Collections.emptyList(), reason);
    }

    public Kind getKind() {
        return kind;
    }

    public List<Shape> getShapes() {
        return shapes;
    }

    public String getReason() {
        return reason;
    }
}
