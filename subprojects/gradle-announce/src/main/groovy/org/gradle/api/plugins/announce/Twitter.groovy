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

package org.gradle.api.plugins.announce;



import org.slf4j.LoggerFactory
import sun.misc.BASE64Encoder
import org.slf4j.Logger

/**
 * This class allows to send announce messages to twitter.
 *
 * @author hackergarten
 */
class Twitter implements Announcer {

  private static final String TWITTER_UPDATE_URL = "https://twitter.com/statuses/update.xml"

  def username
  def password

  private static Logger logger = LoggerFactory.getLogger(Twitter)

  Twitter(String username, String password) {
    this.username = username
    this.password = password
  }

  public void send(String title, String message) {
    OutputStreamWriter out
    URLConnection connection
    try {
      connection = new URL(TWITTER_UPDATE_URL).openConnection()
      connection.doInput = true
      connection.doOutput = true
      connection.useCaches = false
      String encoded = new BASE64Encoder().encodeBuffer("$username:$password".toString().bytes).trim()
      connection.setRequestProperty "Authorization", "Basic " + encoded
      out = new OutputStreamWriter(connection.outputStream)
      out.write "status=" + URLEncoder.encode(message, "UTF-8")
      out.close()
       def result = ''
       connection.inputStream.eachLine { result += it }
      logger.info result
      logger.info("Successfully send message: [$message] to twitter [$username]")
    } catch (Exception e) {
      logger.warn('Could not send message to twitter', e)
    } finally {
      connection?.disconnect()

    }
  
  }
}
