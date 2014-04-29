#import <Foundation/Foundation.h>
#include <iostream>

int main (int argc, const char * argv[])
{
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

    NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
    NSData *strData = [@"Hello " dataUsingEncoding: NSASCIIStringEncoding];
    [stdout writeData: strData];
    std::cout << "world!" << std::endl;

    [pool drain];

    return 0;
}