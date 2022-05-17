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

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DynamicInvokeResult {

    private static final Object NO_VALUE = new Object();
    private static final DynamicInvokeResult NULL = new DynamicInvokeResult(null, null);

    public static DynamicInvokeResult found(Object value) {
        return value == null ? found() : new DynamicInvokeResult(value, null);
    }

    public static DynamicInvokeResult found() {
        return DynamicInvokeResult.NULL;
    }

    public static DynamicInvokeResult notFound() {
        return new DynamicInvokeResult(NO_VALUE, null);
    }

    public static DynamicInvokeResult notFound(@Nullable AdditionalContext additionalContext) {
        return new DynamicInvokeResult(NO_VALUE, additionalContext);
    }

    private final List<AdditionalContext> additionalContexts;
    private final Object value;


    private DynamicInvokeResult(Object value, @Nullable AdditionalContext additionalContext) {
        this.value = value;
        this.additionalContexts = new ArrayList<>(1);
        if (additionalContext != null) {
            this.additionalContexts.add(additionalContext);
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

    public boolean hasAdditionalContext() {
        return !additionalContexts.isEmpty();
    }

    public List<AdditionalContext> getAdditionalContext() {
        return Collections.unmodifiableList(additionalContexts);
    }

    public void addAdditionalContexts(List<AdditionalContext> additionalContexts) {
        Preconditions.checkState(value == NO_VALUE, "Can't set additional context when a method is found");
        this.additionalContexts.addAll(additionalContexts);
    }

    public void addAdditionalContext(AdditionalContext additionalContext) {
        this.additionalContexts.add(additionalContext);
    }

    public void addAdditionalContext(DynamicInvokeResult result) {
        addAdditionalContexts(result.additionalContexts);
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
