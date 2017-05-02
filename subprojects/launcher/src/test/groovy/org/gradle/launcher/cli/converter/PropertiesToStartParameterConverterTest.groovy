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
import org.gradle.api.logging.LogLevel
import spock.lang.Specification

import static org.gradle.launcher.daemon.configuration.GradleProperties.*

class PropertiesToStartParameterConverterTest extends Specification {

    def converter = new PropertiesToStartParameterConverter()

    def "converts"() {
        expect:
        converter.convert([(WORKERS_PROPERTY): "37"], new StartParameter()).maxWorkerCount == 37
        converter.convert([(PARALLEL_PROPERTY): "true"], new StartParameter()).parallelProjectExecutionEnabled
        converter.convert([(BUILD_CACHE_PROPERTY): "true"], new StartParameter()).buildCacheEnabled
        converter.convert([(CONFIGURE_ON_DEMAND_PROPERTY): "TRUE"], new StartParameter()).configureOnDemand
        !converter.convert([(CONFIGURE_ON_DEMAND_PROPERTY): "xxx"], new StartParameter()).configureOnDemand

        converter.convert([(LOG_LEVEL_PROPERTY): "DEBUG"], new StartParameter()).logLevel == LogLevel.DEBUG
        converter.convert([(LOG_LEVEL_PROPERTY): "Info"], new StartParameter()).logLevel == LogLevel.INFO
        converter.convert([(LOG_LEVEL_PROPERTY): "LifeCycle"], new StartParameter()).logLevel == LogLevel.LIFECYCLE
        converter.convert([(LOG_LEVEL_PROPERTY): "warn"], new StartParameter()).logLevel == LogLevel.WARN
        converter.convert([(LOG_LEVEL_PROPERTY): "Error"], new StartParameter()).logLevel == LogLevel.ERROR
        converter.convert([(LOG_LEVEL_PROPERTY): "quiet"], new StartParameter()).logLevel == LogLevel.QUIET
    }

    def invalidMaxWorkersProperty() {
        when:
        converter.convert([(WORKERS_PROPERTY): "invalid"], new StartParameter())
        then:
        thrown(IllegalArgumentException)
    }

    def invalidLogLevel() {
        when:
        converter.convert([(LOG_LEVEL_PROPERTY): "fakeLevel"], new StartParameter())
        then:
        def ex = thrown(IllegalArgumentException)
        ex.getMessage().contains(LOG_LEVEL_PROPERTY)
        LogLevel.values().each { level ->
            ex.getMessage().contains(level.toString())
        }
    }

}
