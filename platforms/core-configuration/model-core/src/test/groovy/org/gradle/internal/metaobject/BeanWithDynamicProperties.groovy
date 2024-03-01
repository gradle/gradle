/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.metaobject

class BeanWithDynamicProperties {
    String prop

    Object propertyMissing(String name) {
        if (name == "dyno") {
            return "ok"
        }
        throw new MissingPropertyException(name, BeanWithDynamicProperties)
    }

    void propertyMissing(String name, Object value) {
        if (name == "dyno") {
            return
        }
        throw new MissingPropertyException(name, BeanWithDynamicProperties)
    }

    String thing(Number l) {
        return l.toString()
    }

    Object methodMissing(String name, Object params) {
        if (name == "dyno") {
            return Arrays.toString((Object[]) params)
        }
        throw new groovy.lang.MissingMethodException(name, BeanWithDynamicProperties, (Object[]) params)
    }
}
