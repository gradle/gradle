/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.googletest.plugins

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.testing.AbstractTestFrameworkIntegrationTest
import org.gradle.util.TextUtil

class GoogleTestFrameworkIntegrationTest extends AbstractTestFrameworkIntegrationTest {
    @Override
    void createPassingFailingTest() {
        def prebuiltDir = buildContext.getSamplesDir().file("native-binaries/google-test/libs")
        def prebuiltPath = TextUtil.normaliseFileSeparators(prebuiltDir.path)

        def app = new CppHelloWorldApp()
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'google-unit-test'

            def googleTestHeaders = file("${prebuiltPath}/googleTest/1.7.0/include")
            def googleTestStaticLib = file("${prebuiltPath}/googleTest/1.7.0/lib/osx64/${googleTestLib}")

            dependencies {
                cppCompileUnitTestExecutable files(googleTestHeaders)
                nativeLinkUnitTestExecutable files(googleTestStaticLib)
            }
        """

        app.library.writeSources(file("src/main"))
        file("src/unitTest/cpp/main.cpp") << """
#include "gtest/gtest.h"

using namespace testing;

int main(int argc, char **argv) {
  testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
"""
        file("src/unitTest/cpp/passing.cpp") << """
#include "gtest/gtest.h"
#include "hello.h"
#include <iostream>

using namespace testing;

TEST(SomeOtherTest, passing) {
  ASSERT_TRUE(sum(2, 2) == 4);
}
"""
        file("src/unitTest/cpp/failing.cpp") << """
#include "gtest/gtest.h"
#include "hello.h"
#include <iostream>

using namespace testing;

TEST(SomeTest, failing) {
  std::cout << "some error output" << std::endl;
  ASSERT_TRUE(sum(2, 2) == 5) << "test failure message";
}
"""
    }

    @Override
    String getTestTaskName() {
        return "runUnitTest"
    }

    @Override
    String getPassingTestCaseName() {
        return "passing"
    }

    @Override
    String getFailingTestCaseName() {
        return "failing"
    }

    private def getGoogleTestLib() {
        return OperatingSystem.current().getStaticLibraryName("gtest")
    }
}
