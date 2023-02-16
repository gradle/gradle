/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.model;

import org.objectweb.asm.Type;

import javax.lang.model.element.ExecutableElement;
import java.util.List;

public class CallInterceptionRequestImpl implements CallInterceptionRequest {
    private final CallableInfo interceptedCallable;
    private final Type implementationOwner;
    private final String implementationName;
    private final String implementationDescriptor;
    private final List<RequestFlag> requestFlags;
    private final ExecutableElement originatingElement;

    public CallInterceptionRequestImpl(
        ExecutableElement originatingElement,
        CallableInfo interceptedCallable,
        Type implementationOwner,
        String implementationName,
        String implementationDescriptor,
        List<RequestFlag> requestFlags
    ) {
        this.originatingElement = originatingElement;
        this.interceptedCallable = interceptedCallable;
        this.implementationOwner = implementationOwner;
        this.implementationName = implementationName;
        this.implementationDescriptor = implementationDescriptor;
        this.requestFlags = requestFlags;
    }

    @Override
    public ExecutableElement getOriginatingElement() {
        return originatingElement;
    }

    @Override
    public CallableInfo getInterceptedCallable() {
        return interceptedCallable;
    }

    @Override
    public Type getImplementationOwner() {
        return implementationOwner;
    }

    @Override
    public String getImplementationName() {
        return implementationName;
    }

    @Override
    public String getImplementationDescriptor() {
        return implementationDescriptor;
    }

    @Override
    public List<RequestFlag> getRequestFlags() {
        return requestFlags;
    }
}
