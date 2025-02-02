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

package org.gradle.api.problems.internal;

import com.google.common.base.Objects;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

import java.util.Map;

public class DefaultTypedAdditionalData implements TypedAdditionalData {
    private final Map<String, Object> data;
    private final SerializedPayload type;

    public DefaultTypedAdditionalData(Map<String, Object> data, SerializedPayload type) {
        this.data = data;
        this.type = type;
    }

    @Override
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public Object getSerializedType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultTypedAdditionalData)) {
            return false;
        }
        DefaultTypedAdditionalData that = (DefaultTypedAdditionalData) o;
        return Objects.equal(data, that.data) && Objects.equal(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data, type);
    }
}
