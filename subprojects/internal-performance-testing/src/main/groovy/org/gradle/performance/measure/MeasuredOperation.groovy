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

package org.gradle.performance.measure

import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.joda.time.DateTime

@ToString(includeNames = true)
@CompileStatic
public class MeasuredOperation {
    DateTime start
    DateTime end
    Amount<Duration> totalTime
    Exception exception

    boolean isValid() {
        exception == null &&
            start!=null &&
            end != null &&
            totalTime != null
    }
}
