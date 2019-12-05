#include <iostream>

int main () {
    #if defined(__clang__)
        std::cout << "Hello from clang!" << std::endl;
    #elif defined(__GNUC__) && defined(__MINGW32__)
        std::cout << "Hello from mingw!" << std::endl;
    #elif defined(__GNUC__) && defined(__CYGWIN__)
        std::cout << "Hello from gcc cygwin!" << std::endl;
    #elif defined(__GNUC__)
        std::cout << "Hello from gcc!" << std::endl;
    #elif defined(_MSC_VER)
        std::cout << "Hello from visual c++!" << std::endl;
    #else
        std::cout << "Hello from an unrecognised tool chain!" << std::endl;
    #endif
  return 0;
}
