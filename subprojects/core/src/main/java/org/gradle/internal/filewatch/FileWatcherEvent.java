/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch;

import org.gradle.api.Nullable;

import java.io.File;

public class FileWatcherEvent {

    public enum Type {
        CREATE,
        MODIFY,
        DELETE,
        UNDEFINED // something happened, but we don't know what
    }

    private final Type type;
    private final File file;

    private FileWatcherEvent(Type type, File file) {
        this.type = type;
        this.file = file;
    }

    public Type getType() {
        return type;
    }

    @Nullable // null if type == UNDEFINED
    public File getFile() {
        return file;
    }

    @Override
    public String toString() {
        return "FileWatcherEvent{type=" + type + ", file=" + file + '}';
    }

    public static FileWatcherEvent create(File file) {
        return new FileWatcherEvent(Type.CREATE, file);
    }

    public static FileWatcherEvent modify(File file) {
        return new FileWatcherEvent(Type.MODIFY, file);
    }

    public static FileWatcherEvent delete(File file) {
        return new FileWatcherEvent(Type.DELETE, file);
    }

    public static FileWatcherEvent undefined() {
        return new FileWatcherEvent(Type.UNDEFINED, null);
    }

}
