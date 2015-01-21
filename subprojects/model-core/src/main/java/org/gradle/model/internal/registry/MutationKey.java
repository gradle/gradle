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

package org.gradle.model.internal.registry;

import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelPath;

class MutationKey {
    final ModelPath path;
    final ModelActionRole type;

    public MutationKey(ModelPath path, ModelActionRole type) {
        this.path = path;
        this.type = type;
    }

    @Override
    public boolean equals(Object obj) {
        MutationKey other = (MutationKey) obj;
        return path.equals(other.path) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return path.hashCode() ^ type.hashCode();
    }
}
