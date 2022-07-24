/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.problems

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

@CompileStatic
class Closures {
    static <T> void configure(
        @DelegatesTo.Target("target") T target,
        @DelegatesTo(value=DelegatesTo.Target.class,
                     target="target",
                     strategy=Closure.DELEGATE_FIRST)
        @ClosureParams(FirstParam.class) Closure<?> cl) {
        cl.delegate = target
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl(target)
    }
}
