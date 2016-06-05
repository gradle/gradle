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

import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.plugins.announce.Announcer;
import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

public class Snarl implements Announcer {

    private static final float SNP_VERSION = 1.1f;
    private static final String HEAD = "type=SNP#?version=" + SNP_VERSION;
    private final IconProvider iconProvider;

    public Snarl(IconProvider iconProvider) {
        this.iconProvider = iconProvider;
    }

    @Override
    public void send(String title, String message) {
        try {
            send(InetAddress.getByName(null), title, message);
        } catch (UnknownHostException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void send(InetAddress host, final String title, final String message) {
        Socket socket = null;
        try {
            try {
                socket = new Socket(host, 9887);
            } catch (ConnectException e) {
                // Snarl is not running
                throw new AnnouncerUnavailableException("Snarl is not running on host " + String.valueOf(host) + ".", e);
            }
            PrintWriter printWriter = null;
            try {
                final OutputStream outputStream = socket.getOutputStream();
                printWriter = new PrintWriter(outputStream, true);
                printWriter.println(formatMessage(title, message));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                IOUtils.closeQuietly(printWriter);
            }
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        } finally {
            IOUtils.closeQuietly(socket);
        }
    }

    private String formatMessage(String title, String message) {
        final File icon = iconProvider.getIcon(32, 32);
        List<String> properties = Arrays.asList(
            formatProperty("action", "notification"),
            formatProperty("app", "Gradle Snarl Notifier"),
            formatProperty("class", "alert"),
            formatProperty("title", title),
            formatProperty("text", message),
            formatProperty("icon", icon == null ? null : icon.getAbsolutePath()),
            formatProperty("timeout", "10"));
        return HEAD + CollectionUtils.join("", properties) + "\r\n";
    }

    private static String formatProperty(String name, String value) {
        if (value != null && !value.isEmpty()) {
            return "#?" + name + "=" + value;
        } else {
            return "";
        }
    }
}
