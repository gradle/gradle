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
package org.gradle.api.plugins.announce

import org.junit.Ignore

class NotifySendTest extends GroovyTestCase {

  public void testWithException() {
    use(ExceptionCategory) {
      def notifier = new NotifySend()
      notifier.send("title", "body")
    }
  }

 /* @Ignore
  public void testCanSendMessage() {

    use(MockCategory) {
      def notifier = new NotifySend()
      notifier.send("title", "body")
      assert ['notify-send', 'title', 'body'] == MockCategory.capture, "nothing was executed"
    }
  }*/

  public void testIntegrationTest() {
    def notifier = new NotifySend()
    notifier.send("title", "body")
  }
}


private static class ExceptionCategory {
  void execute(List list) {
    throw new IOException()
  }
}

private static class MockCategory {
  def static capture

  void execute(List list) {
    capture = list
  }
}


