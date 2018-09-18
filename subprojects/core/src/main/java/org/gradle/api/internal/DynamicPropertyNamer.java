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
package org.gradle.api.internal;

import groovy.lang.GroovyObject;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Named;
import org.gradle.api.Namer;

import java.util.Map;

public class DynamicPropertyNamer implements Namer<Object> {
    public String determineName(Object thing) {
        Object name;
        try {
            if (thing instanceof Named) {
                name = ((Named) thing).getName();
            } else if (thing instanceof Map) {
                name = ((Map) thing).get("name");
            } else if (thing instanceof GroovyObject) {
                name = ((GroovyObject) thing).getProperty("name");
            } else {
                name = DynamicObjectUtil.asDynamicObject(thing).getProperty("name");
            }
        } catch (MissingPropertyException e) {
            throw new NoNamingPropertyException(thing);
        }

        if (name == null) {
            throw new NullNamingPropertyException(thing);
        }

        return name.toString();
    }
}
