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
     * @return comparator that compares operations, slowest first
     */
    public static Comparator<? super Operation> comparator() {
        return new Comparator<Operation>() {
            public int compare(Operation o1, Operation o2) {
                return Long.valueOf(o2.getElapsedTime()).compareTo(Long.valueOf(o1.getElapsedTime()));
            }
        };
    }
}
