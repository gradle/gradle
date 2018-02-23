#include "gtest/gtest.h"
#include "operators.h"

using namespace testing;

TEST(OperatorTests, test_plus) {
  ASSERT_TRUE(plus(0, 2) == 2);
  ASSERT_TRUE(plus(0, -2) == -2);
  ASSERT_TRUE(plus(2, 2) == 4);
}
