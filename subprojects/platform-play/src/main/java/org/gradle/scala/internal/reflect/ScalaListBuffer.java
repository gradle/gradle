/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.scala.internal.reflect;

import java.lang.reflect.Method;
import java.util.List;

public class ScalaListBuffer {
    public static <T> Object fromList(ClassLoader cl, List<T> list) {
        try {
            Class<?> bufferClass = cl.loadClass("scala.collection.mutable.ListBuffer");
            Object buffer = bufferClass.newInstance();
            Method bufferPlusEq = bufferClass.getMethod("$plus$eq", Object.class);

            for (T elem : list) {
                bufferPlusEq.invoke(buffer, elem);
            }
            return buffer;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

    }
}
