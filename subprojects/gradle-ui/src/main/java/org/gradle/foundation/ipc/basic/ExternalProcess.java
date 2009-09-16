/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.foundation.ipc.basic;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.*;

/**
   <p>Helper class for running an external process.  This handles starting/stopping
   and pumping the IO for the process.  If you don't provide a sink for the
   output, it will save it for retrieval as a String.</p>
   <p>Start the process running with {@link #start}</p>
   @author sappling
*/
public class ExternalProcess {
    private final Logger logger = Logging.getLogger(ExternalProcess.class);

    InputStream procInput;
    OutputStream procOutput;
    ProcessBuilder pb;
    Process process;
    IOPump inPump, outPump, errPump;
    Thread inThread, outThread, errThread;
    boolean handleOutput = false;
    ByteArrayOutputStream internalProcessOutput;
    public String inputString = "";


    /**
       Create an ExternalProcess that takes no input and saves all output as a
       String.
       @param  commandLine variable number of Strings for the command line, one
               String for each param.
    */
    public ExternalProcess(String... commandLine) {
        this(null, null, null, commandLine);
    }

    /**
       Create an ExternalProcess that takes no input and saves all output as a
       String.
       @param  workingDirectory the working directory for the new process.
       @param  commandLine variable number of Strings for the command line, one
               String for each param.
    */
    public ExternalProcess(File workingDirectory, String... commandLine) {
        this(null, null, workingDirectory, commandLine);
    }

    /**
       Create an ExternalProcess using the specified Input and Output streams.
       @param  procInput   InputStream for process.
       @param  procOutput  OutputStream for process.
       @param  commandLine variable number of Strings for the command line, one
               String for each param.
    */
    public ExternalProcess(InputStream procInput, OutputStream procOutput, String... commandLine) {
        this(procInput, procOutput, null, commandLine);
    }

    public ExternalProcess(InputStream procInput, OutputStream procOutput, File workingDirectory, String... commandLine) {
        pb = new ProcessBuilder(commandLine);
        if (workingDirectory != null)
            pb.directory(workingDirectory);
        this.procInput = procInput;
        this.procOutput = procOutput;
    }

    public void setInput(String inData) {
        inputString = inData;
    }

    public String getOutput() {
        String result = "";
        if (handleOutput) {
            result = internalProcessOutput.toString();
        }
        return result;
    }

    public void start() throws IOException {
        if (procOutput == null) {
            handleOutput = true;
            internalProcessOutput = new ByteArrayOutputStream();
            procOutput = internalProcessOutput;
        }

        if (procInput == null) {
            procInput = new ByteArrayInputStream(inputString.getBytes());
        }

        process = pb.start();   //may throw an exception

        inPump = new IOPump(process.getInputStream(), procOutput);
        errPump = new IOPump(process.getErrorStream(), procOutput);
        outPump = new IOPump(procInput, process.getOutputStream());

        inThread = new Thread(inPump);
        outThread = new Thread(outPump);
        errThread = new Thread(errPump);
        inThread.start();
        outThread.start();
        errThread.start();
    }

    public int waitFor() throws InterruptedException {
        int result = process.waitFor();
        //Thread.sleep(1000);
        finishPumping();
        return result;
    }

    public void stop() throws InterruptedException {
        process.destroy();
        finishPumping();
        stopPumps();
    }

    public void directory(File file) {
        pb.directory(file);
    }

    public boolean isRunning() {
        boolean running = true;
        try {
            process.exitValue();
            running = false;
        }
        catch (Throwable th) {
        }
        return running;
    }

    public void finalize() throws Throwable {
        stop();
    }

    private void stopPumps() throws InterruptedException {
        inPump.stopNow();
        outPump.stopNow();
        errPump.stopNow();
        inThread.join();
        outThread.join();
        errThread.join();
    }

    private void finishPumping() throws InterruptedException {
        inPump.finish();
        outPump.finish();
        inThread.join();
        outThread.join();
    }

    private class IOPump implements Runnable {
        InputStream in;
        OutputStream out;
        boolean running = false;
        boolean finish = false;

        byte buffer[] = new byte[100];

        IOPump(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        public void run() {
            running = true;
            try {
                while (running) {

                    try {
                        if (!copyData()) {
                            Thread.sleep(100);
                        }
                    }
                    catch (IOException e) {
                        logger.error("Error copying data", e);
                    }
                }
                if (finish) {
                    try {
                        while (copyData()) ;
                    } catch (IOException e) {
                        logger.error("Error copying data", e);
                    }
                }
            }
            catch (InterruptedException e) {
            } // ignore
        }

        private boolean copyData() throws IOException {
            int available = in.available();
            if (available > 0) {
                available = Math.min(available, buffer.length);

                int bytesRead = in.read(buffer, 0, available);
                if (bytesRead > 0) {
                    out.write(buffer, 0, bytesRead);
                }
                return true;
            }
            return false;
        }

        public void stopNow() {
            running = false;
        }

        public void finish() {
            finish = true;
            stopNow();
        }
    }
}
