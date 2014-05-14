#import "greeter.h"

@implementation Greeter

- (void) sayHello {
    NSString *helloWorld = @"Hello world!";
    fprintf(stdout, "%s\n", [helloWorld UTF8String]);
}

@end
