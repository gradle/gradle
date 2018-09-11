/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.profile;

import java.util.Comparator;

/**
 * A general operation.
 */
public abstract class Operation {
    /**
     * Returns the total elapsed execution time of this operation in millis.
     */
    abstract long getElapsedTime();

    abstract String getDescription();

    /**
     * @return comparator that compares operations, slowest first, then alphabetically
     */
    public static Comparator<? super Operation> slowestFirst() {
        return new Comparator<Operation>() {
            @Override
            public int compare(Operation o1, Operation o2) {
                long byElapsedTime = o2.getElapsedTime() - o1.getElapsedTime();
                if (byElapsedTime > 0) {
                    return 1;
                } else if (byElapsedTime < 0) {
                    return -1;
                }
                return o1.getDescription().compareTo(o2.getDescription());
            }
        };
    }
}
