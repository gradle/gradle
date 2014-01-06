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
#include <CUnit/Basic.h>
#include "gradle_cunit_register.h"

/*
 *  Generated launcher for CUnit tests. All tests and suites must be registered in a single method:
 *      void gradle_cunit_register();
 */
int main() {
    CU_initialize_registry();

    gradle_cunit_register();

    CU_basic_set_mode(CU_BRM_VERBOSE);
    CU_basic_run_tests();
    int failureCount = CU_get_number_of_failures();
    CU_cleanup_registry();

    return failureCount == 0 ? 0 : -1;
}
