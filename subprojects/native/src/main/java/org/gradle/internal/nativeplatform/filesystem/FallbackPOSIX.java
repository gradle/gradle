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
import org.jruby.ext.posix.Group;
import org.jruby.ext.posix.POSIX;
import org.jruby.ext.posix.Passwd;

import java.io.FileDescriptor;
import java.io.IOException;

public class FallbackPOSIX implements POSIX {

    static final int ENOTSUP = 1;

    public int chmod(String filename, int mode) {
        return 0;
    }

    public int chown(String filename, int user, int group) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int fork() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public FileStat fstat(FileDescriptor descriptor) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getegid() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int geteuid() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int seteuid(int euid) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getgid() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public String getlogin() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getpgid() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getpgid(int pid) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getpgrp() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getpid() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getppid() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getpriority(int which, int who) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public Passwd getpwent() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public Passwd getpwuid(int which) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public Passwd getpwnam(String which) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public Group getgrgid(int which) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public Group getgrnam(String which) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public Group getgrent() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int endgrent() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setgrent() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int endpwent() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setpwent() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int getuid() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public boolean isatty(FileDescriptor descriptor) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int kill(int pid, int signal) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int lchmod(String filename, int mode) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int lchown(String filename, int user, int group) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int link(String oldpath, String newpath) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public FileStat lstat(String path) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int mkdir(String path, int mode) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public String readlink(String path) throws IOException {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setsid() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setgid(int gid) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setegid(int egid) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setpgid(int pid, int pgid) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setpgrp(int pid, int pgrp) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setpriority(int which, int who, int prio) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int setuid(int uid) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public FileStat stat(String path) {
        return new FallbackFileStat(path);
    }

    public int symlink(String oldpath, String newpath) {
        return ENOTSUP;
    }

    public int umask(int mask) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int utimes(String path, long[] atimeval, long[] mtimeval) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int waitpid(int pid, int[] status, int flags) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int wait(int[] status) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public int errno() {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

    public void errno(int value) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }
}
