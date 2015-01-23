/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.gradle.api.tasks.compile.AbstractOptions
import spock.lang.Specification

abstract class BaseScalaOptionTest<T extends AbstractOptions> extends Specification {

    protected T compileOptions

    abstract T testObject()

    def setup() {
        compileOptions = testObject()
    }

    def contains(String key) {
        compileOptions.optionMap().containsKey(key)
    }

    def doesNotContain(String key) {
        !contains(key)
    }

    def value(String key) {
        compileOptions.optionMap().get(key)
    }


}
