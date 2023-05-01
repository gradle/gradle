/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

public class JavaCallerForBasicCallInterceptorTest {

    public static String doCallSayHello(InheritedMethodTestReceiver receiver) {
        return receiver.sayHello();
    }

    public static String doCallSayHello(InheritedMethodTestReceiver.A receiver) {
        return receiver.sayHello();
    }

    public static String doCallSayHello(InheritedMethodTestReceiver.B receiver) {
        return receiver.sayHello();
    }

    public static void doTestNoArg(InterceptorTestReceiver receiver) {
        receiver.test();
    }

    public static void doTestSingleArg(InterceptorTestReceiver receiver) {
        receiver.test(receiver);
    }

    public static void doTestVararg(InterceptorTestReceiver receiver) {
        receiver.testVararg(receiver, receiver, receiver);
    }
}
