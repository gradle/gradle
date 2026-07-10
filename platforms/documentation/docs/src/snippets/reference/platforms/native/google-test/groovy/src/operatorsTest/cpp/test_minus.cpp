#include "gtest/gtest.h"
#include "operators.h"

using namespace testing;

TEST(OperatorTests, test_minus) {
  ASSERT_TRUE(minus(2, 0) == 2);
  ASSERT_TRUE(minus(0, -2) == 2);
  ASSERT_TRUE(minus(2, 2) == 0);
}
