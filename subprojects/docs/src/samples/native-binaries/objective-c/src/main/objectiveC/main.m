#import <Foundation/Foundation.h>

int main (int argc, const char * argv[])
{
    NSString *helloWorld = @"Hello world!\n";
    NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
    NSData *strData = [helloWorld dataUsingEncoding: NSASCIIStringEncoding];
    [stdout writeData: strData];
    return 0;
}