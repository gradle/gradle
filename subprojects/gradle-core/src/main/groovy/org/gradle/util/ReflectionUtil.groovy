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
package org.gradle.util

import org.apache.commons.lang.StringUtils

/**
 * @author Hans Dockter
 */
class ReflectionUtil {
    public static Object invoke(Object object, String method, Object ... params) {
        return object.invokeMethod(method, params)
    }

    static <T> T newInstance(Class cl, Object ... args) {
        return cl.newInstance(args)
    }

    static Object getProperty(def object, String property) {
        object.getProperty(property)
    }

    static void setProperty(def object, String property, Object value) {
        object."$property" = value
    }

    static boolean hasProperty(def object, String property) {
        return object.metaClass.hasProperty(object, property)
    }

    public static void installGetter(Object target, String name) {
        String capName = name
        // Groovy wants the first character to be lower case when the second char is upper case
        if (name.length() == 1 || !Character.isUpperCase(name.charAt(1))) {
            capName = StringUtils.capitalize(name)
        }
        target.metaClass."get$capName" = {
            target.asDynamicObject.getProperty(name)
        }
    }

    public static void installConfigureMethod(Object target, String name) {
        if (target.metaClass.respondsTo(target, name, Closure)) {
            return
        }
        target.metaClass."$name" << {
            Closure cl -> ConfigureUtil.configure(cl, target[name])
        }
    }
}
