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

package org.gradle.api.publish.internal

import org.gradle.internal.reflect.Instantiator

class GroovyPublicationContainer extends DefaultPublicationContainer {
    GroovyPublicationContainer(Instantiator instantiator) {
        super(instantiator)
    }

    def methodMissing(String name, args) {
        if (args.length == 1 && args[0] instanceof Class) {
            return add(name, args[0]);
        }
        if (args.length == 2 && args[0] instanceof Class && args[1] instanceof Closure) {
            return add(name, args[0], args[1])
        }
        throw new MissingMethodException(name, this.class, args)
    }
}
