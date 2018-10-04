/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.Lists
import org.gradle.api.Action
import spock.lang.Specification

class ChainedTransformerTest extends Specification {

    def "is cached if all parts are cached"() {
        given:
        def chain = new ArtifactTransformationChain(new CachingTransformation(), new CachingTransformation())

        expect:
        chain.hasCachedResult(new File("foo"))
    }

    def "is not cached if first part is not cached"() {
        given:
        def chain = new ArtifactTransformationChain(new NonCachingTransformation(), new CachingTransformation())

        expect:
        !chain.hasCachedResult(new File("foo"))
    }

    def "is not cached if second part is not cached"() {
        given:
        def chain = new ArtifactTransformationChain(new CachingTransformation(), new NonCachingTransformation())

        expect:
        !chain.hasCachedResult(new File("foo"))
    }

    def "applies second transform on the result of the first"() {
        given:
        def chain = new ArtifactTransformationChain(new CachingTransformation(), new NonCachingTransformation())

        expect:
        chain.transform(new File("foo")) == [new File("foo/cached/non-cached")]
    }

    class CachingTransformation implements ArtifactTransformation {

        @Override
        List<File> transform(File input) {
            Lists.newArrayList(new File(input, "cached"));
        }

        @Override
        TransformationSubject transform(TransformationSubject subject) {
            return null
        }

        @Override
        boolean hasCachedResult(File input) {
            return true
        }

        @Override
        String getDisplayName() {
            return null;
        }

        @Override
        void visitTransformationSteps(Action<? super ArtifactTransformation> action) {
        }
    }

    class NonCachingTransformation implements ArtifactTransformation {

        @Override
        List<File> transform(File input) {
            Lists.newArrayList(new File(input, "non-cached"));
        }

        @Override
        TransformationSubject transform(TransformationSubject subject) {
            return null
        }

        @Override
        boolean hasCachedResult(File input) {
            return false
        }

        @Override
        String getDisplayName() {
            return null;
        }

        @Override
        void visitTransformationSteps(Action<? super ArtifactTransformation> action) {
        }
    }
}
