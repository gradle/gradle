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
package org.gradle.nativebinaries.language.cpp
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec

// TODO:DAZ Add unit tests to TestApp and use it here
class CUnitIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "can build and run cunit test suite"() {
        given:
        buildFile << """
            apply plugin: "c"
            apply plugin: "cunit"

            libraries {
                math {}
            }
            binaries.withType(TestSuiteExecutableBinary) {
                linker.args "-lcunit"
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/math/headers/math.h") << """
int maxInt(int i1, int i2);
        """
        file("src/math/c/math.c") << """
int maxInt(int i1, int i2) {
    return (i1 > i2) ? i1 : i2;
}
        """

        and:
        file("src/mathTest/cunit/mathtest.c") << """
#include <CUnit/Basic.h>
#include "math.h"

int init_mathtest(void) {
    return 0;
}

int clean_mathtest(void) {
    return 0;
}

void test_maxInt(void) {
  CU_ASSERT(maxInt(0, 2) == 2);
  CU_ASSERT(maxInt(0, -2) == 0);
  CU_ASSERT(maxInt(2, 2) == 2);
}

int main() {
    CU_initialize_registry();

    CU_pSuite pSuiteMath = CU_add_suite("math test", init_mathtest, clean_mathtest);
    CU_add_test(pSuiteMath, "test of maxInt", test_maxInt);

    CU_basic_set_mode(CU_BRM_VERBOSE);
    CU_basic_run_tests();
    int failureCount = CU_get_number_of_failures();
    CU_cleanup_registry();

    return failureCount == 0 ? 0 : -1;
}
"""

        when:
        run "runMathTestCUnitExe"

        then:
        executedAndNotSkipped ":compileMathTestCUnitExeMathC", ":compileMathTestCUnitExeMathC",
                              ":linkMathTestCUnitExe", ":mathTestCUnitExe", ":runMathTestCUnitExe"
        output.contains """
Suite: math test
  Test: test of maxInt ...passed

Run Summary:    Type  Total    Ran Passed Failed Inactive
              suites      1      1    n/a      0        0
               tests      1      1      1      0        0
             asserts      3      3      3      0      n/a
"""
    }
}
