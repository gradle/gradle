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

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.ParallelismBuildOptions
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import spock.lang.Specification
import spock.lang.Unroll

class PropertiesToStartParameterConverterTest extends Specification {

    def converter = new PropertiesToStartParameterConverter()

    def "converts"() {
        expect:
        converter.convert([(ParallelismBuildOptions.MaxWorkersOption.GRADLE_PROPERTY): "37"], new StartParameterInternal()).maxWorkerCount == 37
        converter.convert([(ParallelismBuildOptions.ParallelOption.GRADLE_PROPERTY): "true"], new StartParameterInternal()).parallelProjectExecutionEnabled
        converter.convert([(StartParameterBuildOptions.BuildCacheOption.GRADLE_PROPERTY): "true"], new StartParameterInternal()).buildCacheEnabled
        converter.convert([(StartParameterBuildOptions.ConfigureOnDemandOption.GRADLE_PROPERTY): "TRUE"], new StartParameterInternal()).configureOnDemand
        !converter.convert([(StartParameterBuildOptions.ConfigureOnDemandOption.GRADLE_PROPERTY): "xxx"], new StartParameterInternal()).configureOnDemand
    }

    def invalidMaxWorkersProperty() {
        when:
        converter.convert([(ParallelismBuildOptions.MaxWorkersOption.GRADLE_PROPERTY): "invalid"], new StartParameterInternal())
        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "converts log levels"() {
        expect:
        converter.convert([(LoggingConfigurationBuildOptions.LogLevelOption.GRADLE_PROPERTY): level], new StartParameterInternal()).logLevel == logLevel

        where:
        level       | logLevel
        'quiet'     | LogLevel.QUIET
        'warn'      | LogLevel.WARN
        'LifeCycle' | LogLevel.LIFECYCLE
        'Info'      | LogLevel.INFO
        'DEBUG'     | LogLevel.DEBUG
    }

    def "throws exception for invalid log level"() {
        when:
        converter.convert([(LoggingConfigurationBuildOptions.LogLevelOption.GRADLE_PROPERTY): "fakeLevel"], new StartParameterInternal())

        then:
        def ex = thrown(IllegalArgumentException)
        ex.getMessage().contains(LoggingConfigurationBuildOptions.LogLevelOption.GRADLE_PROPERTY)
        LogLevel.values().each { level ->
            if(level != LogLevel.ERROR) {
                ex.getMessage().contains(level.toString())
            }
        }
    }

}
