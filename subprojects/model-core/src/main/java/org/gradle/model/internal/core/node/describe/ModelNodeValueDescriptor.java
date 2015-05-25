/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.core.node.describe;

import com.google.common.base.Optional;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.model.internal.core.MutableModelNode;

public class ModelNodeValueDescriptor implements ModelPropertyDescriptor {

    private final MutableModelNode mutableModelNode;

    public ModelNodeValueDescriptor(MutableModelNode mutableModelNode) {
        this.mutableModelNode = mutableModelNode;
    }

    @Override
    public Optional<String> describe() {
        return Optional.fromNullable(toStringOrNull());
    }

    private String toStringOrNull() {
        Object privateData = mutableModelNode.getPrivateData();
        if (null != privateData && !JavaReflectionUtil.hasDefaultToString(privateData)) {
            return privateData.toString();
        }
        return null;
    }
}
