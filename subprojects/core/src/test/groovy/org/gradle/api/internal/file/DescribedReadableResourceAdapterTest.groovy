/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.file;


import org.gradle.api.internal.DescribedReadableResource
import org.gradle.api.resources.ReadableResource
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 11/23/11
 */
public class DescribedReadableResourceAdapterTest extends Specification {

    def "provides names for undescribed resources"() {
        when:
        def d  = new DescribedReadableResourceAdapter(Mock(ReadableResource))
        def d2 = new DescribedReadableResourceAdapter(Mock(ReadableResource))

        then:
        d.uniqueName != d2.uniqueName
        d.name == d2.name
    }

    def "delegates to the resource"() {
        given:
        def mock = Mock(DescribedReadableResource)
        def d  = new DescribedReadableResourceAdapter(mock)

        when:
        d.name
        d.uniqueName
        d.read()

        then:
        1 * mock.name
        1 * mock.uniqueName
        1 * mock.read()
    }
}