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

package org.gradle.model.internal.manage.schema.extract

import groovy.transform.CompileStatic
import org.codehaus.groovy.reflection.ClassInfo

@CompileStatic
class ModelStoreTestUtils {
    private final static Object GLOBAL_CLASS_SET
    private final static Iterable<ClassInfo> GLOBAL_CLASS_SET_ITEMS

    static {
        def f = ClassInfo.getDeclaredField("globalClassSet")
        f.setAccessible(true)
        GLOBAL_CLASS_SET = f.get(null)
        f = GLOBAL_CLASS_SET.class.getDeclaredField('items')
        f.setAccessible(true)
        GLOBAL_CLASS_SET_ITEMS = (Iterable<ClassInfo>) f.get(GLOBAL_CLASS_SET)
    }

    static synchronized void removeClassFromGlobalClassSet(Class<?> clazz) {
        Iterator<ClassInfo> it = GLOBAL_CLASS_SET_ITEMS.iterator()
        while (it.hasNext()) {
            ClassInfo ci = it.next()
            if (ci.cachedClass.getTheClass().is(clazz)) {
                it.remove()
                break
            }
        }
    }
}
