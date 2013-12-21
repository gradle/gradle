/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.progress;

import org.gradle.logging.ProgressLogger;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

public class OperationsHierarchyKeeper {

    private final AtomicLong sharedCounter = new AtomicLong();
    private final ThreadLocal<LinkedList<Long>> hierarchy = new ThreadLocal<LinkedList<Long>>();

    public OperationsHierarchy currentHierarchy(ProgressLogger parentHint) {
        LinkedList<Long> h = hierarchy.get();
        if (h == null) {
            h = new LinkedList<Long>();
            if (parentHint != null) {
                h.add(parentHint.currentOperationId());
            }
            hierarchy.set(h);
        }
        return new OperationsHierarchy(sharedCounter, h);
    }
}
