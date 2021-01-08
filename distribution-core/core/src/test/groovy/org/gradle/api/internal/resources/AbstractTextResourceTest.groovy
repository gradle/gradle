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

package org.gradle.api.internal.resources

import com.google.common.base.Charsets
import org.gradle.api.resources.TextResource
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

abstract class AbstractTextResourceTest extends AbstractProjectBuilderSpec {
    TextResource resource

    def "read as string"() {
        expect:
        resource.asString() == "contents"
    }

    def "read as reader"() {
        expect:
        def reader = resource.asReader()

        try {
            reader.readLine() == "contents"
        } finally {
            reader.close()
        }
    }

    def "read as file"() {
        expect:
        resource.asFile().text == "contents"
    }

    def "read as file with different encoding"() {
        expect:
        resource.asFile(Charsets.UTF_16.name()).getText(Charsets.UTF_16.name()) == "contents"
    }
}
