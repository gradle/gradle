/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification.model;

import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;

/**
 * Internal representation of a checksum, aimed at <i>verification</i>.
 * A checksum consists of a kind (md5, sha1, ...), a value, but also
 * provides <i>alternatives</i>. Alternatives are checksums which are
 * deemed trusted, because sometimes in a single build we can see different
 * checksums for the same module, because they are sourced from different
 * repositories.
 *
 * In theory, this shouldn't be allowed. However, it's often the case that
 * an artifact, in particular _metadata artifacts_ (POM files, ...) differ
 * from one repository to the other (either by end of lines, additional line
 * at the end of the file, ...). Because they are different doesn't mean that
 * they are compromised, so this is a facility for the user to declare "I know
 * I should use a single source of truth but the infrastructure is hard or
 * impossible to fix so let's trust this source".
 *
 * In addition to the list of alternatives, a checksum also provides a source,
 * which is documentation to explain where a checksum was found.
 */
public class Checksum {
    private final ChecksumKind kind;
    private final String value;
    private final Set<String> alternatives;
    private final String origin;
    private final String reason;
    private final int hashCode;

    public Checksum(ChecksumKind kind, String value, @Nullable Set<String> alternatives, @Nullable String origin, @Nullable String reason) {
        this.kind = kind;
        this.value = value;
        this.alternatives = alternatives == null ? null : ImmutableSet.copyOf(alternatives);
        this.origin = origin;
        this.reason = reason;
        this.hashCode = computeHashcode();
    }

    private int computeHashcode() {
        int result = kind.hashCode();
        result = 31 * result + value.hashCode();
        result = 31 * result + (alternatives != null ? alternatives.hashCode() : 0);
        result = 31 * result + (origin != null ? origin.hashCode() : 0);
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        return result;
    }

    public ChecksumKind getKind() {
        return kind;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public Set<String> getAlternatives() {
        return alternatives;
    }

    @Nullable
    public String getOrigin() {
        return origin;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Checksum checksum = (Checksum) o;

        if (kind != checksum.kind) {
            return false;
        }
        if (!value.equals(checksum.value)) {
            return false;
        }
        if (!Objects.equals(alternatives, checksum.alternatives)) {
            return false;
        }
        if (!Objects.equals(origin, checksum.origin)) {
            return false;
        }
        return Objects.equals(reason, checksum.reason);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

}
