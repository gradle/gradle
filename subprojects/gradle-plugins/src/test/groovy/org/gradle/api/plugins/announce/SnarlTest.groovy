package org.gradle.api.plugins.announce;

import groovy.util.GroovyTestCase;
import groovy.mock.interceptor.*
import org.gradle.api.plugins.announce.Snarl

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
