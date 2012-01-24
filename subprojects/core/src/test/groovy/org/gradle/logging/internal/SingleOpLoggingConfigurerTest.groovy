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

package org.gradle.logging.internal

import org.gradle.api.logging.LogLevel
import org.gradle.util.ConcurrentSpecification

/**
 * by Szczepan Faber, created at: 1/23/12
 */
class SingleOpLoggingConfigurerTest extends ConcurrentSpecification {
    
    def configurer = new SingleOpLoggingConfigurer(Mock(LoggingConfigurer))

    def "configures only once"() {
        when:
        5.times {
            start {
                configurer.configure(LogLevel.DEBUG)
            }
        }
        finished()

        then:
        1 * configurer.delegate.configure(LogLevel.DEBUG)
    }
}
