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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.tasks.compile.BaseForkOptions
import spock.lang.Specification


class ForkOptionsMergerTest extends Specification {

    BaseForkOptions merged

    def setup() {
        def options1 = new BaseForkOptions()
        options1.with {
            memoryInitialSize = '200m'
            memoryMaximumSize = '1g'
            jvmArgs = [" -Dfork=true ", "-Xdebug=false"]
        }

        def options2 = new BaseForkOptions()
        options2.with {
            memoryInitialSize = '1g'
            memoryMaximumSize = '2000m'
            jvmArgs = ["-XX:MaxHeapSize=300m", "-Dfork=true"]
        }

        merged = new ForkOptionsMerger().merge(options1, options2)
    }

    def "takes highest minHeapSize"() {
        expect:
        merged.memoryInitialSize == "1024m"
    }

    def "takes highest maxHeapSize"() {
        expect:
        merged.memoryMaximumSize == "2000m"
    }

    def "concatenates jvmArgs (retaining order, eliminating duplicates)"() {
        expect:
        merged.jvmArgs as List == ["-Dfork=true", "-Xdebug=false", "-XX:MaxHeapSize=300m"]
    }
}
