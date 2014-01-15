#import <Foundation/Foundation.h>

int main (int argc, const char * argv[])
{
    NSString *helloWorld = @"Hello World!";
    printf("%s", [helloWorld UTF8String]);
    return 0;
}