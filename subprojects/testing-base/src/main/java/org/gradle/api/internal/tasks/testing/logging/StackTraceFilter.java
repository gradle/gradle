/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging;

import com.google.common.collect.Lists;
import org.gradle.api.specs.Spec;

import java.util.Arrays;
import java.util.List;

public class StackTraceFilter {
    private final Spec<StackTraceElement> filterSpec;

    public StackTraceFilter(Spec<StackTraceElement> filterSpec) {
        this.filterSpec = filterSpec;
    }

    // stack traces are filtered in call order (from bottom to top)
    public List<StackTraceElement> filter(List<StackTraceElement> stackTrace) {
        List<StackTraceElement> filtered = Lists.newArrayList();
        for (StackTraceElement element : Lists.reverse(stackTrace)) {
            if (filterSpec.isSatisfiedBy(element)) {
                filtered.add(element);
            }
        }
        return Lists.reverse(filtered);
    }

    public List<StackTraceElement> filter(Throwable throwable) {
        return filter(Arrays.asList(throwable.getStackTrace()));
    }
}
