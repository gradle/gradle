/*
 * Copyright 2014 the original author or authors.
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
#include <stdio.h>
#include <CUnit/Automated.h>
#include <CUnit/Basic.h>
#include "gradle_cunit_register.h"

/*
 *  Generated launcher for CUnit tests. All tests and suites must be registered in a single method:
 *      void gradle_cunit_register();
 */
int main() {
    int failureCount;

    CU_initialize_registry();

    gradle_cunit_register();

    CU_list_tests_to_file();
    CU_automated_run_tests();
    failureCount = CU_get_number_of_failures();

    // Write test failures to the console
    if (failureCount > 0) {
        printf("\nThere were test failures:");
        CU_basic_show_failures(CU_get_failure_list());
        printf("\n\n");
    }

    CU_cleanup_registry();

    // TODO Wire a test listener and use it to generate a test event stream and binary results (don't use Automated)

    return failureCount == 0 ? 0 : -1;
}
