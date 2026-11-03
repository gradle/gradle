/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations;

import java.util.concurrent.atomic.AtomicLong;

public class DefaultBuildOperationIdFactory implements BuildOperationIdFactory {
    public static final long ROOT_BUILD_OPERATION_ID_VALUE = 1L;

    private final AtomicLong nextId = new AtomicLong(ROOT_BUILD_OPERATION_ID_VALUE);

    @Override
    public long nextId() {
        return nextId.getAndIncrement();
    }
}
