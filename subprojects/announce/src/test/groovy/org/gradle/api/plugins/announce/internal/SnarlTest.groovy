/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins.announce.internal;


class SnarlTest extends GroovyTestCase {
//	public void testSend() {
//		new Snarl().send("JUnit Test", "Hello from Groovy!");
//	}
//	public void testSend_MultipleHosts() {
//		new Snarl().send(["localhost", "localhost", "localhost"], "JUnit Test", "Hello from Groovy!");
//	}

    void testMockSend() {
//		use(PrintWriterCapture) {
//			new Snarl().send("some title", "some message")
//			assert PrintWriterCapture.capture == "type=SNP#?version=1.1#?action=notification#?app=Gradle Snarl Notifier#?class=alert#?title=some title#?text=some message#?timeout=10\r\n"
//		}
    }

}
class PrintWriterCapture {
    static capture

    static void println(PrintWriter p, String input) {
        capture = input
    }
}
