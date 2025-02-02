/*
 * Copyright 2024 the original author or authors.
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

import javax.annotation.Nullable;

public class DefaultDeprecationData implements DeprecationData {

    private final Type type;

    public DefaultDeprecationData(Type type) {
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultDeprecationData)) {
            return false;
        }
        DefaultDeprecationData that = (DefaultDeprecationData) o;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
    }

    public static AdditionalDataBuilder<DeprecationData> builder(@Nullable DeprecationData from) {
        if(from == null) {
            return new DefaultDeprecationDataBuilder();
        }
        return new DefaultDeprecationDataBuilder(from);
    }

    private static class DefaultDeprecationDataBuilder implements DeprecationDataSpec, AdditionalDataBuilder<DeprecationData> {

        private Type type;

        public DefaultDeprecationDataBuilder() {
            this.type = Type.USER_CODE_DIRECT;
        }
        public DefaultDeprecationDataBuilder(DeprecationData from) {
            this.type = from.getType();
        }

        @Override
        public DeprecationDataSpec type(Type type) {
            this.type = type;
            return this;
        }

        @Override
        public DeprecationData build() {
            return new DefaultDeprecationData(type);
        }
    }
}
