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

package org.gradle.process.internal.health.memory;

import com.google.common.base.Objects;
import org.gradle.api.NonNullApi;

import java.util.Map;

@NonNullApi
public class TestMBeanAttributeProvider implements MBeanAttributeProvider {

    public static final class AttributeKey {
        private final String mbean;
        private final String attribute;
        private final Class<?> type;

        public AttributeKey(String mbean, String attribute, Class<?> type) {
            this.mbean = mbean;
            this.attribute = attribute;
            this.type = type;
        }

        public String mbean() {
            return mbean;
        }

        public String attribute() {
            return attribute;
        }

        public Class<?> type() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AttributeKey that = (AttributeKey) o;
            return Objects.equal(mbean, that.mbean) && Objects.equal(attribute, that.attribute) && Objects.equal(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mbean, attribute, type);
        }
    }

    private final Map<AttributeKey, Object> attributes;

    public TestMBeanAttributeProvider(Map<AttributeKey, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public <T> T getMbeanAttribute(String mbean, String attribute, Class<T> type) {
        Object value = attributes.get(new AttributeKey(mbean, attribute, type));
        if (value == null) {
            throw new UnsupportedOperationException("(" + mbean + ")." + attribute + " is unsupported in this test.");
        }
        return type.cast(value);
    }
}
