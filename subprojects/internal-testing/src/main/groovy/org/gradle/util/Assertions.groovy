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

class Assertions {

    private final Object source

    static Assertions assertThat(Object source) {
        new Assertions(source)
    }

    Assertions(Object source) {
        this.source = source
    }

    Assertions doesNotShareStateWith(Object target) {
        //this is really basic - does not work well with primitives, immutables like String, etc
        //grow it if needed
        source.getClass().getDeclaredFields().each {
            if (!it.name.startsWith('$') && it.name != 'metaClass') { //filter out groovy stuff
                it.setAccessible(true)
                assert !it.get(source).is(it.get(target)) : "field value '$it.name' should not be shared between the instances."
            }
        }
        this
    }
}