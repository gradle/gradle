#include <iostream>
#include "version.hpp"
#include "util.h"

int main () {
  std::cout << "Built with Boost version: " << BOOST_LIB_VERSION << std::endl;
  printBuildType();
  return 0;
}
