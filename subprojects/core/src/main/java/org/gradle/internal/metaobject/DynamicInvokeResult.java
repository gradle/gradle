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

package org.gradle.internal.metaobject;

public abstract class DynamicInvokeResult {

    public static DynamicInvokeResult found(Object value) {
        return value == null ? found() : new Found(value);
    }

    public static DynamicInvokeResult found() {
        return Found.NULL;
    }

    public static DynamicInvokeResult notFound() {
        return NotFound.INSTANCE;
    }

    private DynamicInvokeResult() {
    }

    public abstract Object getValue();

    public abstract boolean isFound();

    private static class NotFound extends DynamicInvokeResult {
        private static final NotFound INSTANCE = new NotFound();

        @Override
        public Object getValue() {
            throw new IllegalStateException("Not found");
        }

        @Override
        public boolean isFound() {
            return false;
        }
    }

    private static class Found extends DynamicInvokeResult {
        private static final Found NULL = new Found(null);

        private final Object value;

        private Found(Object value) {
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public boolean isFound() {
            return true;
        }
    }
}
