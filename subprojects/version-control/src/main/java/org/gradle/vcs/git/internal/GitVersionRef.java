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

package org.gradle.vcs.git.internal;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.gradle.vcs.VersionRef;

import java.util.List;

public class GitVersionRef implements VersionRef {
    private final String version;
    private final String canonicalId;

    private GitVersionRef(String version, String canonicalId) {
        this.version = version;
        this.canonicalId = canonicalId;
    }

    public static GitVersionRef from(Ref ref) {
        ObjectId commitId = ref.getPeeledObjectId() == null ? ref.getObjectId() : ref.getPeeledObjectId();
        return new GitVersionRef(extractName(ref), commitId.getName());
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getCanonicalId() {
        return canonicalId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        GitVersionRef other = (GitVersionRef) obj;
        return Objects.equal(version, other.version) && Objects.equal(canonicalId, other.canonicalId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(version, canonicalId);
    }

    @Override
    public String toString() {
        return version + ": " + canonicalId;
    }

    private static String extractName(Ref ref) {
        List<String> parts = Splitter.on("/").splitToList(ref.getName());
        return parts.get(parts.size()-1);
    }
}
