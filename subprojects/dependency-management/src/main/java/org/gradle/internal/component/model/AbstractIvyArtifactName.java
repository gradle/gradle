/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.model;

import java.util.Objects;

/**
 * Super class for all implementations of {@link IvyArtifactName} which guarantees
 * any subclass is equal if their implementations of {@code IvyArtifactName} methods
 * are functionally equivalent.
 */
public abstract class AbstractIvyArtifactName implements IvyArtifactName {

    @Override
    public final int hashCode() {
        int result = getName().hashCode();
        result = 31 * result + getType().hashCode();
        result = 31 * result + (getExtension() != null ? getExtension().hashCode() : 0);
        result = 31 * result + (getClassifier() != null ? getClassifier().hashCode() : 0);
        return result;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof AbstractIvyArtifactName)) {
            return false;
        }
        AbstractIvyArtifactName other = (AbstractIvyArtifactName) obj;
        return Objects.equals(getName(), other.getName())
            && Objects.equals(getType(), other.getType())
            && Objects.equals(getExtension(), other.getExtension())
            && Objects.equals(getClassifier(), other.getClassifier());
    }
}
