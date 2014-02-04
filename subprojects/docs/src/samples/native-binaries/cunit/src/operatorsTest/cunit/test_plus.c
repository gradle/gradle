#include <CUnit/Basic.h>
#include "operators.h"
#include "test_operators.h"

void test_plus(void) {
  CU_ASSERT(plus(0, 2) == 2);
  CU_ASSERT(plus(0, -2) == -2);
  CU_ASSERT(plus(2, 2) == 4);
}