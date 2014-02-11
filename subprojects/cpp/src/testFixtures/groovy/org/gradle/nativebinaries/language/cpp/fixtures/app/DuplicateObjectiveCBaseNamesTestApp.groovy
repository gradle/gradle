package org.gradle.nativebinaries.language.cpp.fixtures.app

import org.gradle.internal.os.OperatingSystem

class DuplicateObjectiveCBaseNamesTestApp extends TestComponent{

    def plugins = ["objective-c"]
    @Override
    List<SourceFile> getSourceFiles() {
        [sourceFile("objc", "main.m", """
            #import <Foundation/Foundation.h>
            #import "foo.h"

            int main (int argc, const char * argv[])
            {
                sayFoo1();
                sayFoo2();
                return 0;
            }
        """),
          fooSource(1),
          fooSource(2)]
    }

    public String getExtraConfiguration() {
        def linkerArgs = OperatingSystem.current().isMacOsX() ? '"-framework", "Foundation"' : '"-lgnustep-base", "-lobjc"'
        return """
            binaries.all {
                if (toolChain in Gcc) {
                    objcCompiler.args "-I/usr/include/GNUstep", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                }

                if (toolChain in Clang) {
                    objcCompiler.args "-I/usr/include/GNUstep", "-I/usr/local/include/objc", "-fconstant-string-class=NSConstantString", "-D_NATIVE_OBJC_EXCEPTIONS"
                }

                linker.args $linkerArgs
            }
        """
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
        sourceFile("objc/foo$index", "foo.m", """
            #import <Foundation/Foundation.h>
            #import "foo.h"

            void sayFoo$index()
            {
                NSFileHandle *stdout = [NSFileHandle fileHandleWithStandardOutput];
                NSData *strData = [@"foo$index" dataUsingEncoding: NSASCIIStringEncoding];
                [stdout writeData: strData];
            }
        """)
    }
}
