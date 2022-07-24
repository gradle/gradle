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

package org.gradle.api.internal.tasks.properties;

import groovy.lang.GString;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.util.internal.DeferredUtil;

import javax.annotation.Nullable;

public class InputParameterUtils {
    @Nullable
    public static Object prepareInputParameterValue(InputPropertySpec inputProperty, Task task) {
        String propertyName = inputProperty.getPropertyName();
        try {
            return prepareInputParameterValue(inputProperty.getValue());
        } catch (Exception ex) {
            throw new PropertyEvaluationException(task, propertyName, ex);
        }
    }

    @Nullable
    public static Object prepareInputParameterValue(@Nullable Object value) {
        Object unpacked = DeferredUtil.unpackOrNull(value);
        return finalizeValue(unpacked);
    }

    @Nullable
    private static Object finalizeValue(@Nullable Object unpacked) {
        if (unpacked instanceof GString) {
            return unpacked.toString();
        }
        if (unpacked instanceof FileCollection) {
            return ((FileCollection) unpacked).getFiles();
        }
        return unpacked;
    }
}
