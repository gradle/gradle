/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

class CppSumTest extends CppSourceElement implements SumElement {
    final SourceElement headers = empty()
    final SourceElement sources = ofFiles(new SourceFile("cpp", "sum_test.cpp", """
            #include "sum.h"

            int main(int argc, char **argv) {
                Sum sum;
                if (sum.sum(2, 2) == ${sum(2, 2)}) {
                    return 0;
                }
                return -1;
            }"""))

    @Override
    int sum(int a, int b) {
        return a + b
    }
}
