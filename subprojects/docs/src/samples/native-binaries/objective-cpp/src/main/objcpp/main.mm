#define __STDC_LIMIT_MACROS
#include <stdint.h>
#import <Foundation/Foundation.h>
#include <iostream>

int main (int argc, const char * argv[])
{
    NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
    NSData *strData = [@"Hello " dataUsingEncoding: NSASCIIStringEncoding];
    [stdout writeData: strData];
    std::cout << "world!" << std::endl;

    return 0;
}