#include <iostream>
#include "util.h"

void LIB_FUNC printBuildType () {
#ifdef DEBUG
  std::cout << "Util build type: DEBUG" << std::endl;
#else
  std::cout << "Util build type: RELEASE" << std::endl;
#endif
}
