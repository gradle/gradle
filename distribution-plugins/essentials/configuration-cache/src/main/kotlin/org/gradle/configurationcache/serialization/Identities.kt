/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache.serialization

import java.util.IdentityHashMap


class WriteIdentities {

    private
    val instanceIds = IdentityHashMap<Any, Int>()

    fun getId(instance: Any) = instanceIds[instance]

    fun putInstance(instance: Any): Int {
        val id = instanceIds.size
        instanceIds[instance] = id
        return id
    }
}


class ReadIdentities {

    private
    val instanceIds = HashMap<Int, Any>()

    fun getInstance(id: Int) = instanceIds[id]

    fun putInstance(id: Int, instance: Any) {
        instanceIds[id] = instance
    }
}
