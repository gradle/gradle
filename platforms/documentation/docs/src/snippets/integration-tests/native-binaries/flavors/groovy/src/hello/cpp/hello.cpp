#include <iostream>
#include "hello.h"

void LIB_FUNC hello () {
  #ifdef FRENCH
  std::cout << "Bonjour monde!" << std::endl;
  #else
  std::cout << "Hello world!" << std::endl;
  #endif
}
