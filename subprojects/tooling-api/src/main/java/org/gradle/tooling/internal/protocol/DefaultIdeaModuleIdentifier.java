/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.protocol;

import com.google.common.base.Objects;
import org.gradle.tooling.model.idea.IdeaModuleIdentifier;

import java.io.Serializable;

public class DefaultIdeaModuleIdentifier implements IdeaModuleIdentifier, Serializable {
    private final String identifier;

    public DefaultIdeaModuleIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultIdeaModuleIdentifier)) {
            return false;
        }
        DefaultIdeaModuleIdentifier that = (DefaultIdeaModuleIdentifier) o;
        return Objects.equal(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(identifier);
    }

    @Override
    public String toString() {
        return identifier;
    }
}
