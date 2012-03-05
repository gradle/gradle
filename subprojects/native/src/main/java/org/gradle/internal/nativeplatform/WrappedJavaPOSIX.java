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

package org.gradle.internal.nativeplatform;

import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.Group;
import org.jruby.ext.posix.POSIX;
import org.jruby.ext.posix.Passwd;

import java.io.FileDescriptor;
import java.io.IOException;

public class WrappedJavaPOSIX implements POSIX {
    private POSIX delegate;

    public WrappedJavaPOSIX(POSIX posix) {
        this.delegate = posix;
    }

    public int chmod(String filename, int mode) {
        //short circuit to avoid setting uncompatible chmod values for unknown filetypes
        return mode;
    }

    public int chown(String filename, int user, int group) {
        return delegate.chown(filename, user, group);
    }

    public int fork() {
        return delegate.fork();
    }

    public FileStat fstat(FileDescriptor descriptor) {
        return delegate.fstat(descriptor);
    }

    public int getegid() {
        return delegate.getegid();
    }

    public int geteuid() {
        return delegate.geteuid();
    }

    public int seteuid(int euid) {
        return delegate.seteuid(euid);
    }

    public int getgid() {
        return delegate.getgid();
    }

    public String getlogin() {
        return delegate.getlogin();
    }

    public int getpgid() {
        return delegate.getpgid();
    }

    public int getpgid(int pid) {
        return delegate.getpgid(pid);
    }

    public int getpgrp() {
        return delegate.getpgrp();
    }

    public int getpid() {
        return delegate.getpid();
    }

    public int getppid() {
        return delegate.getppid();
    }

    public int getpriority(int which, int who) {
        return delegate.getpriority(which, who);
    }

    public Passwd getpwent() {
        return delegate.getpwent();
    }

    public Passwd getpwuid(int which) {
        return delegate.getpwuid(which);
    }

    public Passwd getpwnam(String which) {
        return delegate.getpwnam(which);
    }

    public Group getgrgid(int which) {
        return delegate.getgrgid(which);
    }

    public Group getgrnam(String which) {
        return delegate.getgrnam(which);
    }

    public Group getgrent() {
        return delegate.getgrent();
    }

    public int endgrent() {
        return delegate.endgrent();
    }

    public int setgrent() {
        return delegate.setgrent();
    }

    public int endpwent() {
        return delegate.endpwent();
    }

    public int setpwent() {
        return delegate.setpwent();
    }

    public int getuid() {
        return delegate.getuid();
    }

    public boolean isatty(FileDescriptor descriptor) {
        return delegate.isatty(descriptor);
    }

    public int kill(int pid, int signal) {
        return delegate.kill(pid, signal);
    }

    public int lchmod(String filename, int mode) {
        return delegate.lchmod(filename, mode);
    }

    public int lchown(String filename, int user, int group) {
        return delegate.lchown(filename, user, group);
    }

    public int link(String oldpath, String newpath) {
        return delegate.link(oldpath, newpath);
    }

    public FileStat lstat(String path) {
        return delegate.lstat(path);
    }

    public int mkdir(String path, int mode) {
        return delegate.mkdir(path, mode);
    }

    public String readlink(String path) throws IOException {
        return delegate.readlink(path);
    }

    public int setsid() {
        return delegate.setsid();
    }

    public int setgid(int gid) {
        return delegate.setgid(gid);
    }

    public int setegid(int egid) {
        return delegate.setegid(egid);
    }

    public int setpgid(int pid, int pgid) {
        return delegate.setpgid(pid, pgid);
    }

    public int setpgrp(int pid, int pgrp) {
        return delegate.setpgrp(pid, pgrp);
    }

    public int setpriority(int which, int who, int prio) {
        return delegate.setpriority(which, who, prio);
    }

    public int setuid(int uid) {
        return delegate.setuid(uid);
    }

    public FileStat stat(String path) {
        return delegate.stat(path);
    }

    public int symlink(String oldpath, String newpath) {
        return delegate.symlink(oldpath, newpath);
    }

    public int umask(int mask) {
        return delegate.umask(mask);
    }

    public int utimes(String path, long[] atimeval, long[] mtimeval) {
        return delegate.utimes(path, atimeval, mtimeval);
    }

    public int waitpid(int pid, int[] status, int flags) {
        return delegate.waitpid(pid, status, flags);
    }

    public int wait(int[] status) {
        return delegate.wait(status);
    }

    public int errno() {
        return delegate.errno();
    }

    public void errno(int value) {
        delegate.errno(value);
    }
}
