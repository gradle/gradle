#import <iostream>
#import "greeter.h"

@implementation Greeter

- (void) sayHello {
    NSString *helloWorld = @"Hello world!";
    std::cout << [helloWorld UTF8String] << std::endl;
}

@end
