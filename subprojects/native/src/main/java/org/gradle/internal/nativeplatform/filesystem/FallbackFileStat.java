/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeplatform.filesystem;

import org.jruby.ext.posix.FileStat;

import java.io.File;

public class FallbackFileStat implements FileStat {

    private final File file;

    public FallbackFileStat(String path) {
        this.file = new File(path);
    }

    public long atime() {
        throw new UnsupportedOperationException("Operation atime() is not supported.");
    }

    public long blocks() {
        throw new UnsupportedOperationException("Operation blocks is not supported.");
    }

    public long blockSize() {
        throw new UnsupportedOperationException("Operation blockSize() is not supported.");
    }

    public long ctime() {
        throw new UnsupportedOperationException("Operation ctime() is not supported.");
    }

    public long dev() {
        throw new UnsupportedOperationException("Operation dev() is not supported.");
    }

    public String ftype() {
        throw new UnsupportedOperationException("Operation ftype() is not supported.");
    }

    public int gid() {
        throw new UnsupportedOperationException("Operation gid() is not supported.");
    }

    public boolean groupMember(int gid) {
        throw new UnsupportedOperationException("Operation groupMember() is not supported.");
    }

    public long ino() {
        throw new UnsupportedOperationException("Operation ino() is not supported.");
    }

    public boolean isBlockDev() {
        throw new UnsupportedOperationException("Operation isBlockDev() is not supported.");
    }

    public boolean isCharDev() {
        throw new UnsupportedOperationException("Operation isCharDev() is not supported.");
    }

    public boolean isDirectory() {
        return file.isDirectory();
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException("Operation isEmpty() is not supported.");
    }

    public boolean isExecutable() {
        throw new UnsupportedOperationException("Operation isExecutable() is not supported.");
    }

    public boolean isExecutableReal() {
        throw new UnsupportedOperationException("Operation isExecutableReal() is not supported.");
    }

    public boolean isFifo() {
        throw new UnsupportedOperationException("Operation isFifo() is not supported.");
    }

    public boolean isFile() {
        return file.isFile();
    }

    public boolean isGroupOwned() {
        throw new UnsupportedOperationException("Operation isGroupOwned() is not supported.");
    }

    public boolean isIdentical(FileStat other) {
        throw new UnsupportedOperationException("Operation isIdentical() is not supported.");
    }

    public boolean isNamedPipe() {
        throw new UnsupportedOperationException("Operation isNamedPipe() is not supported.");
    }

    public boolean isOwned() {
        throw new UnsupportedOperationException("Operation isOwned() is not supported.");
    }

    public boolean isROwned() {
        throw new UnsupportedOperationException("Operation isROwned() is not supported.");
    }

    public boolean isReadable() {
        throw new UnsupportedOperationException("Operation isReadable() is not supported.");
    }

    public boolean isReadableReal() {
        throw new UnsupportedOperationException("Operation isReadableReal() is not supported.");
    }

    public boolean isWritable() {
        throw new UnsupportedOperationException("Operation isWritable() is not supported.");
    }

    public boolean isWritableReal() {
        throw new UnsupportedOperationException("Operation isWritableReal() is not supported.");
    }

    public boolean isSetgid() {
        throw new UnsupportedOperationException("Operation isSetgid() is not supported.");
    }

    public boolean isSetuid() {
        throw new UnsupportedOperationException("Operation isSetuid() is not supported.");
    }

    public boolean isSocket() {
        throw new UnsupportedOperationException("Operation isSocket() is not supported.");
    }

    public boolean isSticky() {
        throw new UnsupportedOperationException("Operation isSticky() is not supported.");
    }

    public boolean isSymlink() {
        throw new UnsupportedOperationException("Operation isSymlink() is not supported.");
    }

    public int major(long dev) {
        throw new UnsupportedOperationException("Operation major() is not supported.");
    }

    public int minor(long dev) {
        throw new UnsupportedOperationException("Operation minor() is not supported.");
    }

    public int mode() {
        if (isDirectory()) {
            return FileSystem.DEFAULT_DIR_MODE;
        } else {
            return FileSystem.DEFAULT_FILE_MODE;
        }
    }

    public long mtime() {
        throw new UnsupportedOperationException("Operation mtime() is not supported.");
    }

    public int nlink() {
        throw new UnsupportedOperationException("Operation nlink() is not supported.");
    }

    public long rdev() {
        throw new UnsupportedOperationException("Operation rdev() is not supported.");
    }

    public long st_size() {
        throw new UnsupportedOperationException("Operation st_size() is not supported.");
    }

    public int uid() {
        throw new UnsupportedOperationException("Operation uid() is not supported.");
    }
}
