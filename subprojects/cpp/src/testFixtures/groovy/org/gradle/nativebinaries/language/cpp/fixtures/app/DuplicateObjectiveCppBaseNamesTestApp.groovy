package org.gradle.nativebinaries.language.cpp.fixtures.app

import org.gradle.internal.os.OperatingSystem

class DuplicateObjectiveCppBaseNamesTestApp extends TestComponent{
    def plugins = ["objective-c"]
    @Override
    List<SourceFile> getSourceFiles() {
        [sourceFile("objc", "main.mm", """
            #import <Foundation/Foundation.h>
            #import "foo.h"

            int main (int argc, const char * argv[])
            {
                sayFoo1();
                sayFoo2();
                return 0;
            }
        """),
            sourceFile("objc/foo1", "foo.mm", """
            #import <Foundation/Foundation.h>
            #import "foo.h"

            void sayFoo1()
            {
                NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
                NSData *strData = [@"foo1" dataUsingEncoding: NSASCIIStringEncoding];
                [stdout writeData: strData];
            }
        """),
                sourceFile("objc/foo2", "foo.mm", """
            #import <iostream>
            #import "foo.h"

            void sayFoo1()
            {
                std::cout << "foo2" << std::endl;
            }
        """)]
    }

    @Override
    List<SourceFile> getHeaderFiles() {
        [sourceFile("headers", "foo.h", """
            void sayFoo1();
            void sayFoo2();
           """)
        ]
    }

    SourceFile fooSource(int index) {

    }

    public String getExtraConfiguration() {
        def linkerArgs = OperatingSystem.current().isMacOsX() ? '"-framework", "Foundation"' : '"-lgnustep-base", "-lobjc"'
        return """
            binaries.all {
                objcppCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                linker.args $linkerArgs
            }
        """
    }
}
