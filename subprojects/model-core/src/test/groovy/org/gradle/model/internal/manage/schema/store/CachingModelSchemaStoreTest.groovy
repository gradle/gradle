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

package org.gradle.model.internal.manage.schema.store

import org.gradle.internal.UncheckedException
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.manage.schema.ModelSchema
import spock.lang.Specification

class CachingModelSchemaStoreTest extends Specification {

    def "runtime exceptions thrown from the backing store are unwrapped"() {
        given:
        def exception = new RuntimeException("from backing store")
        def extractor = Mock(ModelSchemaExtractor) {
            extract(_, _) >> { throw exception }
        }

        when:
        new CachingModelSchemaStore(extractor).getSchema(ModelType.of(String))

        then:
        RuntimeException thrown = thrown()
        thrown == exception
    }

    def "checked exceptions thrown from the backing store are unwrapped"() {
        given:
        def exception = new Exception("from backing store")
        def extractor = new ModelSchemaExtractor(null) {
            @Override
            def <T> ModelSchema<T> extract(ModelType<T> type, ModelSchemaStore store) {
                throw exception
            }
        }

        when:
        new CachingModelSchemaStore(extractor).getSchema(ModelType.of(String))

        then:
        UncheckedException thrown = thrown()
        thrown.cause == exception
    }
}
