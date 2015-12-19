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

package org.gradle.model.internal.inspect

import org.gradle.model.RuleSource

class WithGroovyMeta extends RuleSource {
    @Override
    Object getProperty(String property) {
        null
    }

    @Override
    Object invokeMethod(String name, Object args) {
        null
    }

    def propertyMissing(String name) {
        null
    }

    def propertyMissing(String name, def value) {
    }

    def methodMissing(String name, def args) {
    }
}
