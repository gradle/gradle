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

import javax.annotation.Nullable;

public class DynamicInvokeResult {

    private static final Object NO_VALUE = new Object();
    private static final DynamicInvokeResult NOT_FOUND = new DynamicInvokeResult(NO_VALUE);
    private static final DynamicInvokeResult NULL = new DynamicInvokeResult(null);

    public static DynamicInvokeResult found(@Nullable Object value) {
        return value == null ? found() : new DynamicInvokeResult(value);
    }

    public static DynamicInvokeResult found() {
        return DynamicInvokeResult.NULL;
    }

    public static DynamicInvokeResult notFound() {
        return DynamicInvokeResult.NOT_FOUND;
    }

    private final Object value;

    private DynamicInvokeResult(@Nullable Object value) {
        this.value = value;
    }

    @Nullable
    public Object getValue() {
        if (isFound()) {
            return value;
        } else {
            throw new IllegalStateException("Not found");
        }
    }

    public boolean isFound() {
        return value != NO_VALUE;
    }
}
