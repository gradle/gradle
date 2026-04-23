#import "greeter.h"

int main (int argc, const char * argv[])
{
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

    Greeter *greeter = [Greeter new];
    [greeter sayHello];
    [greeter release];

    [pool drain];

    return 0;
}