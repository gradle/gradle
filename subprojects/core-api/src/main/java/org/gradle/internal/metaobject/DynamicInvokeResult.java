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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DynamicInvokeResult {
    private static final ThreadLocal<List<AdditionalContext>> CURRENT_ADDITIONAL_CONTEXT = new ThreadLocal<>();
    static {
        CURRENT_ADDITIONAL_CONTEXT.set(new ArrayList<>());
    }

    private static final Object NO_VALUE = new Object();
    private static final DynamicInvokeResult NULL = new DynamicInvokeResult(null, null);

    public static boolean hasAdditionalContext() {
        return !CURRENT_ADDITIONAL_CONTEXT.get().isEmpty();
    }

    public static List<AdditionalContext> getAdditionalContext() {
        return Collections.unmodifiableList(CURRENT_ADDITIONAL_CONTEXT.get());
    }

    public static DynamicInvokeResult found(Object value) {
        if (value != NO_VALUE) {
            CURRENT_ADDITIONAL_CONTEXT.set(new ArrayList<>()); // Clear context when a result is found
        }
        return value == null ? found() : new DynamicInvokeResult(value, null);
    }

    public static DynamicInvokeResult found() {
        CURRENT_ADDITIONAL_CONTEXT.get().clear(); // Clear context when a result is found
        return DynamicInvokeResult.NULL;
    }

    public static DynamicInvokeResult notFound() {
        return new DynamicInvokeResult(NO_VALUE, null);
    }

    public static DynamicInvokeResult notFound(@Nullable AdditionalContext additionalContext) {
        return new DynamicInvokeResult(NO_VALUE, additionalContext);
    }

    private final Object value;


    private DynamicInvokeResult(Object value, @Nullable AdditionalContext additionalContext) {
        this.value = value;
        if (additionalContext != null) {
            CURRENT_ADDITIONAL_CONTEXT.get().add(additionalContext);
        }
    }

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

    public interface AdditionalContext {
        String getMessage();

        static AdditionalContext forString(String message) {
            return new DefaultAdditionalContext(message);
        }

        class DefaultAdditionalContext implements AdditionalContext {
            private final String message;

            private DefaultAdditionalContext(String message) {
                this.message = message;
            }

            public String getMessage() {
                return message;
            }
        }
    }
}
