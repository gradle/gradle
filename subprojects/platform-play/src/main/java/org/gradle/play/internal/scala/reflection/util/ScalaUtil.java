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

package org.gradle.play.internal.scala.reflection.util;

import com.google.common.base.Function;
import org.gradle.internal.UncheckedException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ScalaUtil {
    /**
     * Invokes a method on a scala object
     */
    public static Function<Object[], Object> scalaObjectFunction(ClassLoader classLoader, String objectName, String methodName, Class<?>[] typeParameters) {
        try {
            Class<?> baseClass = classLoader.loadClass(objectName + "$");

            final Field scalaObject = baseClass.getField("MODULE$");
            final Method scalaObjectMethod = scalaObject.getType().getMethod(methodName, typeParameters);

            Function<Object[], Object> function = new Function<Object[], Object>() {
                public Object apply(Object[] args) {
                    try {
                        return scalaObjectMethod.invoke(scalaObject.get(null), args);
                    } catch (Exception e) {
                        throw new RuntimeException("Cannot invoke scala method", e);
                    }
                }

                public boolean equals(Object object) {
                    return false;
                }

                public int hashCode() {
                    return scalaObjectMethod.hashCode() + scalaObject.hashCode(); //This is a random hashcode. We had to have a hashCode here because Function requires an equals, but it feels wrong. Maybe using Function is not the best option.
                }
            };
            return function;
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (NoSuchFieldException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
