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

package org.gradle.testing.internal.util

/**
 * Helps testing if the exception messages chain (including causes)
 * contain given information.
 * <p>
 * by Szczepan Faber, created at: 3/6/12
 */
public class ExceptionAssert {

    private final Throwable target

    public ExceptionAssert(Throwable target) {
        this.target = target
    }

    public static assertThat(Throwable t) {
        return new ExceptionAssert(t);
    }

    /**
     * Asserts if given exception message/ exception classname contains given information (substring)
     * Recursively checks the cause chain.
     *
     * @param information wanted substring
     */
    void containsInfo(String information) {
        def ex = target
        def checked = []
        while(ex != null) {
            checked << ex.toString()
            if (ex.toString().contains(information)) {
                return
            }
            
            ex = ex.cause
        }
        throw new AssertionError((Object) "Unable to find '$information' in the exception messages chain."
            + "\nTested following messages: \n------\n > ${checked.join('\n > ')}\n------")
    }

    String toString() {
        return "target=$target"
    }
}
