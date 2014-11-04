/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.instance;

import org.gradle.model.internal.manage.schema.ModelSchema;

public interface ModelInstantiator {

    /*
        There are a few problems with this general instantiation framework.

        We end up with some correlated validation type logic between the schema extraction stuff and the instantiators.
        We don't want to push the instantiation logic into the schema layer because that should be purely data.
        Instantiation may also end up being contextual, and schema representation probably shouldn't be contextual.

        For now, we are just living with the duplication.

        LD.
     */

    public <T> T newInstance(ModelSchema<T> schema);

}
