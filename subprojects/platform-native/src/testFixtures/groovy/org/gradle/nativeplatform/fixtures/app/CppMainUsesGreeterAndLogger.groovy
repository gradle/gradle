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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile


class CppMainUsesGreeterAndLogger extends SourceFileElement implements AppElement {
    final GreeterElement greeter

    CppMainUsesGreeterAndLogger(GreeterElement greeter) {
        this.greeter = greeter
    }

    final SourceFile sourceFile = sourceFile("cpp", "main.cpp", """
    #include <string>
    #include "greeter.h"

    class Logger {
        public:
        void log(std::string &str);
    };

    int main(int argc, char** argv) {
        Logger logger;
        Greeter greeter;

        std::string sayingHello = "Saying hello...";
        logger.log(sayingHello);
        greeter.sayHello();

        return 0;
    }
    """)

    @Override
    String getExpectedOutput() {
        return "Saying hello...\n${greeter.expectedOutput}"
    }
}
