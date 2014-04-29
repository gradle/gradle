#import <Foundation/Foundation.h>

int main (int argc, const char * argv[])
{
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

    NSString *helloWorld = @"Hello world!\n";
    NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
    NSData *strData = [helloWorld dataUsingEncoding: NSASCIIStringEncoding];
    [stdout writeData: strData];

    [pool drain];

    return 0;
}