/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher;

import com.sun.jna.*;
import org.gradle.api.GradleException;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.gradle.util.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.launcher.DaemonStartAction.Kernel32.*;

public class DaemonStartAction {
    private final List<String> args = new ArrayList<String>();
    private File dir = GFileUtils.canonicalise(new File("."));

    public void args(Iterable<String> args) {
        GUtil.addToCollection(this.args, args);
    }

    public void workingDir(File dir) {
        this.dir = dir;
    }

    public void start() {
        try {
            dir.mkdirs();
            if (OperatingSystem.current().isWindows()) {
                // Use the native Windows CreateProcessW() function, instead of Java's Process.
                // This is so we can create a fully detached process. This prevents, for example, the child process inheriting our stdout
                // and stderr handles, so that our (java) parent process does not block waiting for our stdout/stderr to complete after
                // this process exits.
                //
                // Of course, this also means that we can't use the child process's stdout/stderr/stdin streams for anything useful.
                //
                // TODO - figure out how to close the handles inherited from our parent process
                StringBuilder commandLine = new StringBuilder();
                for (String arg : args) {
                    commandLine.append('"');
                    commandLine.append(arg);
                    commandLine.append("\" ");
                }

                Kernel32 kernel32 = INSTANCE;
                StartupInfo startupInfo = new StartupInfo();
                ProcessInfo processInformation = new ProcessInfo();
                if (!kernel32.CreateProcessW(null, new WString(commandLine.toString()), null, null, false, DETACHED_PROCESS, null,
                        new WString(dir.getAbsolutePath()), startupInfo, processInformation)) {
                    throw new IOException("Could not start process. Errno: " + kernel32.GetLastError());
                }
                kernel32.CloseHandle(processInformation.hProcess);
                kernel32.CloseHandle(processInformation.hThread);
            } else {
                ProcessBuilder builder = new ProcessBuilder(args);
                builder.directory(dir);
                Process process = builder.start();
                process.getOutputStream().close();
                process.getErrorStream().close();
                process.getInputStream().close();
            }
        } catch (Exception e) {
            throw new GradleException("Could not start Gradle daemon.", e);
        }
    }

    public interface Kernel32 extends Library {
        Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);

        // Process creation flags
        int DETACHED_PROCESS = 0x00000008;

        int GetLastError();

        boolean CloseHandle(HANDLE hObject);

        boolean CreateProcessW(WString lpApplicationName, WString lpCommandLine, SecurityAttributes lpProcessAttributes,
                               SecurityAttributes lpThreadAttributes, boolean bInheritHandles, int dwCreationFlags,
                               Pointer lpEnvironment, WString lpCurrentDirectory, StartupInfo lpStartupInfo,
                               ProcessInfo lpProcessInformation);

        class HANDLE extends PointerType {
            public HANDLE() {
            }

            public HANDLE(Pointer p) {
                super(p);
            }
        }

        class SecurityAttributes extends Structure {
            public int nLength;
            public Pointer lpSecurityDescriptor;
            public boolean bInheritHandle;

            public SecurityAttributes() {
                nLength = size();
            }
        }

        class StartupInfo extends Structure {
            public int cb;
            public WString lpReserved;
            public WString lpDesktop;
            public WString lpTitle;
            public int dwX;
            public int dwY;
            public int dwXSize;
            public int dwYSize;
            public int dwXCountChars;
            public int dwYCountChars;
            public int dwFillAttribute;
            public int dwFlags;
            public short wShowWindow;
            public short cbReserved2;
            public Pointer lpReserved2;
            public HANDLE hStdInput;
            public HANDLE hStdOutput;
            public HANDLE hStdError;

            public StartupInfo() {
                cb = size();
            }
        }

        class ProcessInfo extends Structure {
            public HANDLE hProcess;
            public HANDLE hThread;
            public int dwProcessId;
            public int dwThreadId;
        }
    }
}
