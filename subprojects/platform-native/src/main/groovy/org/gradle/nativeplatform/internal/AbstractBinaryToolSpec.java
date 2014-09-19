/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.internal;

import org.gradle.nativeplatform.platform.NativePlatform;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AbstractBinaryToolSpec implements BinaryToolSpec {
    private List<String> args = new ArrayList<String>();
    private List<String> systemArgs = new ArrayList<String>();
    private File tempDir;
    private NativePlatform platform;

    public NativePlatform getTargetPlatform() {
        return platform;
    }

    public void setTargetPlatform(NativePlatform platform) {
        this.platform = platform;
    }

    public File getTempDir() {
        return tempDir;
    }

    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

    public List<String> getArgs() {
        return args;
    }

    public void args(List<String> args) {
        this.args.addAll(args);
    }

    public List<String> getSystemArgs() {
        return systemArgs;
    }

    public void systemArgs(List<String> args) {
       if(!systemArgs.containsAll(args)){
           systemArgs.addAll(args);
       }
    }

    public List<String> getAllArgs() {
        List<String> allArgs = new ArrayList<String>(systemArgs.size() + args.size());
        allArgs.addAll(systemArgs);
        allArgs.addAll(args);
        return allArgs;
    }
}
