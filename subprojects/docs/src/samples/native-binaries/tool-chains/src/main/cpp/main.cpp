#include <iostream>

int main () {
    #if defined(__clang__)
        std::cout << "Hello from Clang!" << std::endl;
    #elif defined(__GNUC__) && defined(__MINGW32__)
        std::cout << "Hello from MinGW!" << std::endl;
    #elif defined(__GNUC__) && defined(__CYGWIN__)
        std::cout << "Hello from GCC cygwin!" << std::endl;
    #elif defined(__GNUC__)
        std::cout << "Hello from GCC!" << std::endl;
    #elif defined(_MSC_VER)
        std::cout << "Hello from Visual C++!" << std::endl;
    #else
        std::cout << "Hello from an unrecognised tool chain!" << std::endl;
    #endif
  return 0;
}
