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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import spock.lang.Specification

class DefaultTaskClassInfoStoreTest extends Specification {
    def taskClassInfoStore = new DefaultTaskClassInfoStore(new TestCrossBuildInMemoryCacheFactory())

    @CacheableTask
    private static class MyCacheableTask extends DefaultTask {}

    def "cacheable tasks are detected"() {
        expect:
        taskClassInfoStore.getTaskClassInfo(MyCacheableTask).cacheable
    }

    private static class MyNonCacheableTask extends MyCacheableTask {}

    def "cacheability is not inherited"() {
        expect:
        !taskClassInfoStore.getTaskClassInfo(MyNonCacheableTask).cacheable
    }


    private static class NonAnnotatedTask extends DefaultTask {
        File inputFile

        @SuppressWarnings("GrMethodMayBeStatic")
        String getValue() {
            "test"
        }
    }

    def "class infos are cached"() {
        def info = taskClassInfoStore.getTaskClassInfo(NonAnnotatedTask)
        expect:
        info.is(taskClassInfoStore.getTaskClassInfo(NonAnnotatedTask))
    }
}
