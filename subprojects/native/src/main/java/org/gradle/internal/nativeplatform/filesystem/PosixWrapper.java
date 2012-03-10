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

import org.jruby.ext.posix.*;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Util class to wrap / modify results of calls to the {@link org.jruby.ext.posix.POSIXFactory#getPOSIX(org.jruby.ext.posix.POSIXHandler, boolean)}
 * */
public class PosixWrapper {

    public static POSIX wrap(POSIX posix) {
        if (posix instanceof JavaPOSIX) {
            return new ChmodDisabledFallbackPOSIX(posix);
        } else if (posix instanceof WindowsPOSIX) {
            return new FilePermissionFallbackPOSIX(posix);
        } else {
            return posix;
        }
    }

    private static class ChmodDisabledFallbackPOSIX extends FilePermissionFallbackPOSIX {

        public ChmodDisabledFallbackPOSIX(POSIX posix) {
            super(posix);
        }

        public int chmod(String filename, int mode) {
            // short circuit this call to return incoming mode
            return mode;
        }
    }

    private static class FilePermissionFallbackPOSIX extends DelegatePOSIX{
        public FilePermissionFallbackPOSIX(POSIX posix) {
            super(posix);
        }

        public FileStat stat(String path) {
            return new FallbackFileStat(super.stat(path));
        }
    }

    /**
     * a Plain DelegatePosix to keep own posix implementations "clean"
     * */
    private abstract static class DelegatePOSIX implements POSIX {
        private POSIX delegate;

        public DelegatePOSIX(POSIX posix) {
            this.delegate = posix;
        }

        public int chmod(String filename, int mode) {
            return delegate.chmod(filename, mode);
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
            return new FallbackFileStat(delegate.stat(path));
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

    /**
     * Calls to {@link #mode()} return 664 for files and 755 for directories.
     * Other method calls are delegated to delegate FileStat.
     * */
    private static class FallbackFileStat implements FileStat {

        private FileStat delegate;

        public FallbackFileStat(FileStat stat) {
            this.delegate = stat;
        }

        public long atime() {
            return delegate.atime();
        }

        public long blocks() {
            return delegate.blocks();
        }

        public long blockSize() {
            return delegate.blockSize();
        }

        public long ctime() {
            return delegate.ctime();
        }

        public long dev() {
            return delegate.dev();
        }

        public String ftype() {
            return delegate.ftype();
        }

        public int gid() {
            return delegate.gid();
        }

        public boolean groupMember(int gid) {
            return delegate.groupMember(gid);
        }

        public long ino() {
            return delegate.ino();
        }

        public boolean isBlockDev() {
            return delegate.isBlockDev();
        }

        public boolean isCharDev() {
            return delegate.isCharDev();
        }

        public boolean isDirectory() {
            return delegate.isDirectory();
        }

        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        public boolean isExecutable() {
            return delegate.isExecutable();
        }

        public boolean isExecutableReal() {
            return delegate.isExecutableReal();
        }

        public boolean isFifo() {
            return delegate.isFifo();
        }

        public boolean isFile() {
            return delegate.isFile();
        }

        public boolean isGroupOwned() {
            return delegate.isGroupOwned();
        }

        public boolean isIdentical(FileStat other) {
            return delegate.isIdentical(other);
        }

        public boolean isNamedPipe() {
            return delegate.isNamedPipe();
        }

        public boolean isOwned() {
            return delegate.isOwned();
        }

        public boolean isROwned() {
            return delegate.isROwned();
        }

        public boolean isReadable() {
            return delegate.isReadable();
        }

        public boolean isReadableReal() {
            return delegate.isReadableReal();
        }

        public boolean isWritable() {
            return delegate.isWritable();
        }

        public boolean isWritableReal() {
            return delegate.isWritableReal();
        }

        public boolean isSetgid() {
            return delegate.isSetgid();
        }

        public boolean isSetuid() {
            return delegate.isSetuid();
        }

        public boolean isSocket() {
            return delegate.isSocket();
        }

        public boolean isSticky() {
            return delegate.isSticky();
        }

        public boolean isSymlink() {
            return delegate.isSymlink();
        }

        public int major(long dev) {
            return delegate.major(dev);
        }

        public int minor(long dev) {
            return delegate.minor(dev);
        }

        public int mode() {
            if(isDirectory()){
                return FileSystem.DEFAULT_DIR_MODE;
            }else{
                return FileSystem.DEFAULT_FILE_MODE;
            }
        }

        public long mtime() {
            return delegate.mtime();
        }

        public int nlink() {
            return delegate.nlink();
        }

        public long rdev() {
            return delegate.rdev();
        }

        public long st_size() {
            return delegate.st_size();
        }

        public int uid() {
            return delegate.uid();
        }
    }
}
