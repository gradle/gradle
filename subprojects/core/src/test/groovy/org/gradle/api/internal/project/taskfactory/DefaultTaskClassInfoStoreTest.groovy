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
import org.gradle.api.tasks.UntrackedTask
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.properties.annotations.TypeMetadata
import org.gradle.internal.properties.annotations.TypeMetadataStore
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata
import spock.lang.Specification

class DefaultTaskClassInfoStoreTest extends Specification {
    def typeMetadataStore = Mock(TypeMetadataStore)
    def taskClassInfoStore = new DefaultTaskClassInfoStore(new TestCrossBuildInMemoryCacheFactory(), typeMetadataStore)
    def typeMetadata = Mock(TypeMetadata)
    def typeAnnotationMetadata = Mock(TypeAnnotationMetadata)

    @CacheableTask
    private static class MyCacheableTask extends DefaultTask {}

    def "cacheable tasks are detected"() {
        given:
        1 * typeMetadataStore.getTypeMetadata(MyCacheableTask) >> typeMetadata
        _ * typeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.isAnnotationPresent(CacheableTask) >> true
        1 * typeAnnotationMetadata.getAnnotation(UntrackedTask) >> Optional.empty()
        1 * typeMetadata.getFunctionMetadata() >> []

        expect:
        taskClassInfoStore.getTaskClassInfo(MyCacheableTask).cacheable
    }

    private static class MyNonCacheableTask extends MyCacheableTask {}

    def "cacheability is not inherited"() {
        given:
        1 * typeMetadataStore.getTypeMetadata(MyNonCacheableTask) >> typeMetadata
        _ * typeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.isAnnotationPresent(CacheableTask) >> false
        1 * typeAnnotationMetadata.getAnnotation(UntrackedTask) >> Optional.empty()
        1 * typeMetadata.getFunctionMetadata() >> []

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
        given:
        1 * typeMetadataStore.getTypeMetadata(NonAnnotatedTask) >> typeMetadata
        _ * typeMetadata.getTypeAnnotationMetadata() >> typeAnnotationMetadata
        1 * typeAnnotationMetadata.isAnnotationPresent(CacheableTask) >> false
        1 * typeAnnotationMetadata.getAnnotation(UntrackedTask) >> Optional.empty()
        1 * typeMetadata.getFunctionMetadata() >> []

        def info = taskClassInfoStore.getTaskClassInfo(NonAnnotatedTask)
        expect:
        info.is(taskClassInfoStore.getTaskClassInfo(NonAnnotatedTask))
    }
}
