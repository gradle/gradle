/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.failure;

import org.gradle.internal.problems.StackTraceRelevance;

import javax.annotation.Nullable;

public class CalledFromDroppingStackTraceClassifier implements StackTraceClassifier {

    private final Class<?> calledFrom;
    private Boolean calledFromFound = false;

    public CalledFromDroppingStackTraceClassifier(Class<?> calledFrom) {
        this.calledFrom = calledFrom;
    }

    @Nullable
    @Override
    public StackTraceRelevance classify(StackTraceElement frame) {
        if (calledFromFound == null) {
            return null;
        }

        boolean matches = frame.getClassName().startsWith(calledFrom.getName());
        if (!calledFromFound) {
            if (matches) {
                calledFromFound = true;
            }
        } else {
            if (!matches) {
                calledFromFound = null;
                return null;
            }
        }

        return StackTraceRelevance.SYSTEM;
    }
}
