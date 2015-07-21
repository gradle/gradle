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

package org.gradle.tooling.events.test.internal;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;

/**
 * Implementation of the {@code TestOperationDescriptor} interface.
 */
public class DefaultTestOperationDescriptor extends DefaultOperationDescriptor implements TestOperationDescriptor {

    public DefaultTestOperationDescriptor(InternalTestDescriptor internalTestDescriptor, OperationDescriptor parent) {
        super(internalTestDescriptor, parent);
    }
}
