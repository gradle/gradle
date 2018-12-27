/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.instantiation

import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification


abstract class AbstractClassGeneratorSpec extends Specification {
    abstract ClassGenerator getGenerator()

    protected <T> T create(Class<T> clazz, Object... args) {
        return create(clazz, new DefaultServiceRegistry(), args)
    }

    protected <T> T create(Class<T> clazz, ServiceRegistry services, Object... args) {
        return create(generator, clazz, services, args)
    }

    protected <T> T create(ClassGenerator generator, Class<T> clazz, Object... args) {
        return create(generator, clazz, new DefaultServiceRegistry(), args)
    }

    protected <T> T create(ClassGenerator generator, Class<T> clazz, ServiceRegistry services, Object... args) {
        def type = generator.generate(clazz)
        return type.constructors[0].newInstance(services, Stub(Instantiator), args)
    }
}
