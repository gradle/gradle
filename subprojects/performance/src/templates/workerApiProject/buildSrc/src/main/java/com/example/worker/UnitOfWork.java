/*
 * Copyright 2019 the original author or authors.
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

package com.example.worker;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class UnitOfWork implements Runnable {
    private final int index;
    private final File outputFile;

    @Inject
    public UnitOfWork(int index, File outputFile) {
        this.index = index;
        this.outputFile = outputFile;
    }

    @Override
    public void run() {
        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.append("index is " + index + "\n");
        } catch (IOException e) {
            throw new RuntimeException("could not write to " + outputFile);
        }
    }
}
