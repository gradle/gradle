/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project

import org.gradle.api.AntBuilder
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.api.internal.project.ant.AntLoggingAdapterFactory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

public class DefaultAntBuilderFactoryTest extends AbstractProjectBuilderSpec {
    private AntLoggingAdapterFactory adapterFactory = Stub(AntLoggingAdapterFactory)
    private DefaultAntBuilderFactory factory

    def setup() {
        factory = new DefaultAntBuilderFactory(project, adapterFactory)
        adapterFactory.create() >> Stub(AntLoggingAdapter)
    }

    public void "can create AntBuilder"() {
        when:
        def ant = factory.create()

        then:
        ant != null
        ant instanceof AntBuilder
    }

    public void "sets base directory of Ant project"() {
        when:
        def ant = factory.create()

        then:
        ant.project.baseDir == project.projectDir
    }
}
