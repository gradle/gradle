/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.events.test.internal;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.test.Destination;
import org.gradle.tooling.events.test.TestOutputDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;

public class DefaultTestOutputOperationDescriptor extends DefaultOperationDescriptor implements TestOutputDescriptor {

    private final Destination destination;
    private final String message;

    public DefaultTestOutputOperationDescriptor(InternalOperationDescriptor internalDescriptor, OperationDescriptor parent, Destination destination, String message) {
        super(internalDescriptor, parent);
        this.destination = destination;
        this.message = message;
    }

    @Override
    public Destination getDestination() {
        return destination;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
