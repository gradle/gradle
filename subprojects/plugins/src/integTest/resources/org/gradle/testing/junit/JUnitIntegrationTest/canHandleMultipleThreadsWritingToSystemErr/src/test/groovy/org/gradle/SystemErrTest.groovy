/*
 * Copyright 2012 the original author or authors.
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

package org.gradle

public class SystemErrTest {
    @org.junit.Test
    void test() {
        System.err.println ("thread 0 out")
        def thread1 = Thread.start {
            System.err.println "thread 1 out"
        }
        def thread2 = Thread.start {
            System.err.println "thread 2 out"
        }
        thread1.join()
        thread2.join()
    }
}
