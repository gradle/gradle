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

package org.gradle.nativebinaries.toolchain.internal.gcc.version;

import org.gradle.api.Transformer;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a File pointing to an (existing) g++ binary, extracts the version number by running with -v and scraping the output.
 */
public class GccVersionDeterminer implements Transformer<String, File> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GccVersionDeterminer.class);

    private final Transformer<String, String> outputScraper;
    private final Transformer<String, File> outputProducer;

    public GccVersionDeterminer(ExecActionFactory execActionFactory) {
        this.outputProducer = new GccVersionOutputProducer(execActionFactory);
        this.outputScraper = new GccVersionOutputScraper();
    }

    static class GccVersionOutputScraper implements Transformer<String, String> {
        public String transform(String output) {
            Pattern pattern = Pattern.compile(".*gcc version (\\S+).*", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(output);
            if (matcher.matches()) {
                String scrapedVersion = matcher.group(1);
                LOGGER.debug("Extracted version {} from g++ -v output", scrapedVersion);
                return scrapedVersion;
            } else {
                LOGGER.warn("Unable to extract g++ version number from \"{}\" with pattern \"{}\"", output, pattern);
                return null;
            }
        }
    }

    static class GccVersionOutputProducer implements Transformer<String, File> {
        
        private final ExecActionFactory execActionFactory;

        GccVersionOutputProducer(ExecActionFactory execActionFactory) {
            this.execActionFactory = execActionFactory;
        }

        public String transform(File gccBinary) {
            ExecAction exec = execActionFactory.newExecAction();
            exec.executable(gccBinary.getAbsolutePath());
            exec.setWorkingDir(gccBinary.getParentFile());
            exec.args("-v");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exec.setErrorOutput(baos);
            exec.setIgnoreExitValue(true);
            ExecResult result = exec.execute();

            int exitValue = result.getExitValue();
            if (exitValue == 0) {
                String output = new String(baos.toByteArray());
                LOGGER.debug("Output from '{} -v {}", gccBinary.getPath(), output);
                return output;                
            } else {
                LOGGER.warn("Executing '{} -v' return exit code {}, cannot use", gccBinary.getPath(), exitValue);
                return null;
            }
        }
    }

    public String transform(File gccBinary) {
        String output = outputProducer.transform(gccBinary);
        return output == null ? null : outputScraper.transform(output);
    }
}
