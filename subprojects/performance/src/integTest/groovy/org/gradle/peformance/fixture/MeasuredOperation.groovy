/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.peformance.fixture

/**
 * by Szczepan Faber, created at: 2/10/12
 */
public class MeasuredOperation {
    long executionTime
    Exception exception

    static MeasuredOperation measure(Closure operation) {
        long before = System.currentTimeMillis()
        def out = new MeasuredOperation()
        try {
            operation()
        } catch (Exception e) {
            out.exception = e
        }
        out.executionTime = System.currentTimeMillis() - before
        return out
    }
}
