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

package org.gradle.util;

import org.apache.commons.lang.reflect.FieldUtils;
import org.gradle.internal.UncheckedException;

public class JavaReflectionUtil {
    // TODO: use setter instead of field
    public static void setProperty(Object target, String property, Object value) {
        try {
            FieldUtils.writeDeclaredField(target, property, value, true);
        } catch (IllegalAccessException e) {
            throw new UncheckedException(e);
        }
    }

    // TODO: use getter instead of field
    public static Object getProperty(Object target, String property) {
        try {
            return FieldUtils.readDeclaredField(target, property, true);
        } catch (IllegalAccessException e) {
            throw new UncheckedException(e);
        }
    }
}
