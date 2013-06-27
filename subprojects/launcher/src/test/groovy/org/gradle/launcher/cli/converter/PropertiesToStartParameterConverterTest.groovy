/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.launcher.cli.converter

import org.gradle.StartParameter
import spock.lang.Specification

import static org.gradle.initialization.layout.GradleProperties.CONFIGURE_ON_DEMAND_PROPERTY
import static org.gradle.initialization.layout.GradleProperties.PARALLEL_PROPERTY

/**
 * by Szczepan Faber, created at: 3/11/13
 */
class PropertiesToStartParameterConverterTest extends Specification {

    def converter = new PropertiesToStartParameterConverter()

    def "converts"() {
        expect:
        converter.convert([(PARALLEL_PROPERTY): "true"], new StartParameter()).parallelThreadCount == -1
        converter.convert([(PARALLEL_PROPERTY): "false"], new StartParameter()).parallelThreadCount == 0
        converter.convert([(CONFIGURE_ON_DEMAND_PROPERTY): "TRUE"], new StartParameter()).configureOnDemand
        !converter.convert([(CONFIGURE_ON_DEMAND_PROPERTY): "xxx"], new StartParameter()).configureOnDemand
    }
}
