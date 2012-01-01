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

package org.gradle.api.plugins.announce.internal

import org.gradle.api.plugins.announce.Announcer

class Snarl implements Announcer {
    private static final float SNP_VERSION = 1.1f
    private static final String HEAD = "type=SNP#?version=" + SNP_VERSION
    private final IconProvider iconProvider

    Snarl(IconProvider iconProvider) {
        this.iconProvider = iconProvider
    }

    public void send(String title, String message) {
        send(InetAddress.getByName(null), title, message)
    }

    public void send(InetAddress host, String title, String message) {
        Socket socket
        try {
            socket = new Socket(host, 9887)
        } catch (ConnectException e) {
            // Snarl is not running
            throw new AnnouncerUnavailableException("Snarl is not running on host $host.", e)
        }
        with(socket) { sock ->
            with(new PrintWriter(sock.getOutputStream(), true)) { out ->
                out.println(formatMessage(title, message))
            }
        }
    }

    private String formatMessage(String title, String message) {
        def properties = [
                formatProperty("action", "notification"),
                formatProperty("app", "Gradle Snarl Notifier"),
                formatProperty("class", "alert"),
                formatProperty("title", title),
                formatProperty("text", message),
                formatProperty("icon", iconProvider.getIcon(32, 32)?.absolutePath),
                formatProperty("timeout", "10")]

        HEAD + properties.join('') + "\r\n"
    }

    private String formatProperty(String name, String value) {
        if (value) {
            return "#?" + name + "=" + value
        }
        else {
            return ""
        }
    }

    private with(closable, closure) {
        try {
            closure(closable)
        } finally {
            closable.close()
        }
    }
}
