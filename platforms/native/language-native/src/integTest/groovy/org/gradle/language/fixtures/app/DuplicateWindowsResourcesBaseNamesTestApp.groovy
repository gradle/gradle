/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.fixtures.app

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.nativeplatform.fixtures.app.TestNativeComponent

class DuplicateWindowsResourcesBaseNamesTestApp extends TestNativeComponent {

    def plugins = ["cpp","windows-resources"]

    @Override
    List<SourceFile> getSourceFiles() {
        [sourceFile("cpp", "main.cpp", """
#include "hello.h"

int main () {
    hello();
    return 0;
}
"""),
         sourceFile("cpp", "hello.cpp", """
#include <iostream>
#include <windows.h>
#include <string>
#include "hello.h"

std::string LoadStringFromResource(UINT stringID)
{
    HINSTANCE instance = GetModuleHandle("hello");
    WCHAR * pBuf = NULL;
    int len = LoadStringW(instance, stringID, reinterpret_cast<LPWSTR>(&pBuf), 0);
    std::wstring wide = std::wstring(pBuf, len);
    return std::string(wide.begin(), wide.end());
}

void hello() {
    std::string foo1 = LoadStringFromResource(IDS_FOO1);
    std::string foo2 = LoadStringFromResource(IDS_FOO2);
    std::cout << foo1;
    std::cout << foo2;
}
"""),
        sourceFile("rc/dir1", "resources.rc", """
#include "hello.h"

STRINGTABLE
{
    IDS_FOO1, "foo1"
}
"""),
        sourceFile("rc/dir2", "resources.rc", """
#include "hello.h"

STRINGTABLE
{
    IDS_FOO2, "foo2"
}
""")]

    }

    @Override
    List<SourceFile> getHeaderFiles() {
        return [sourceFile("headers", "hello.h", """
#define IDS_FOO1    111
#define IDS_FOO2    1000

void hello();
""")]
    }
}
