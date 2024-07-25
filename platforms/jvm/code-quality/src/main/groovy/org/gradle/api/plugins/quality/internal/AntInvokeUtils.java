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

package org.gradle.api.plugins.quality.internal;

import groovy.lang.Closure;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

class AntInvokeUtils {

    static void invoke(AntBuilderDelegate receiver, String methodName, Map<String, Object> parameters, Runnable closure) {
        receiver.invokeMethod(methodName, new Object[]{parameters, new Closure<Object>(receiver, receiver) {
            public Object doCall(Object object) {
                closure.run();
                return object;
            }
        }});
    }

    static void invoke(AntBuilderDelegate receiver, String methodName, Map<String, Object> parameters) {
        receiver.invokeMethod(methodName, new Object[]{parameters});
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getProjectProperties(AntBuilderDelegate ant) {
        try {
            Object project = ant.getProperty("project");
            return  (Map<String, Object>) project.getClass().getDeclaredMethod("getProperties").invoke(project);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
