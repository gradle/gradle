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

package gradlebuild.basics;

import org.gradle.util.GradleVersion;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Compatibility support class for upgraded properties that needs to work with Gradle 8 and Gradle 9.
 */
public class Gradle9PropertyUpgradeSupport {

    public static  <T> void setProperty(Object task, String setterName, T value) {
        if (GradleVersion.current().compareTo(GradleVersion.version("9.0")) < 0) {
            setPropertyPreGradle9(task, setterName, value);
        } else {
            setPropertyPostGradle9(task, setterName, value);
        }
    }

    private static void setPropertyPostGradle9(Object task, String setterName, Object value) {
        try {
            // Task is Task_Decorated, so it has set<Property>(Object) setter
            task.getClass().getMethod(setterName, Object.class).invoke(task, value);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setPropertyPreGradle9(Object task, String setterName, Object value) {
        try {
            for (Method method : task.getClass().getMethods()) {
                if (method.getParameters().length == 1
                    && method.getName().equals(setterName)
                    && method.getParameters()[0].getType().isAssignableFrom(value.getClass())) {
                    method.invoke(task, value);
                    return;
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
