#include <CUnit/Basic.h>
#include "operators.h"

void test_minus() {
  CU_ASSERT(minus(2, 0) == 2);
  CU_ASSERT(minus(0, -2) == 2);
  CU_ASSERT(minus(2, 2) == 0);
}
