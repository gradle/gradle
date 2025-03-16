#include "gtest/gtest.h"
#include "${generatedId}/header.h"

using namespace testing;
<%
if (hasTests) {
    out.println """
TEST(GeneratedTests, test_lib) {
    ASSERT_EQ(0, function_${generatedId}());
}"""
}
%>

int main(int argc, char **argv) {
  testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
