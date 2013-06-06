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

package org.gradle.nativecode.toolchain.internal.gpp.version;

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.internal.Factory;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a File pointing to an (existing) g++ binary, extracts the version number by running with -v and scraping the output.
 */
public class GppVersionDeterminer implements Transformer<String, File> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GppVersionDeterminer.class);

    private final Transformer<String, String> outputScraper;
    private final Transformer<String, File> outputProducer;

    public GppVersionDeterminer() {
        this(new GppVersionOutputProducer(new Factory<ExecHandleBuilder>() {
            public ExecHandleBuilder create() {
                return new ExecHandleBuilder(new IdentityFileResolver());
            }
        }), new GppVersionOutputScraper());
    }

    GppVersionDeterminer(Transformer<String, File> outputProducer, Transformer<String, String> outputScraper) {
        this.outputProducer = outputProducer;
        this.outputScraper = outputScraper;
    }

    static class GppVersionOutputScraper implements Transformer<String, String> {
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

    static class GppVersionOutputProducer implements Transformer<String, File> {
        
        private final Factory<ExecHandleBuilder> execHandleBuilderFactory;

        GppVersionOutputProducer(Factory<ExecHandleBuilder> execHandleBuilderFactory) {
            this.execHandleBuilderFactory = execHandleBuilderFactory;
        }

        public String transform(File gppBinary) {
            ExecHandleBuilder exec = execHandleBuilderFactory.create();
            exec.executable(gppBinary.getAbsolutePath());
            exec.setWorkingDir(gppBinary.getParentFile());
            exec.args("-v");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exec.setErrorOutput(baos);
            ExecHandle handle = exec.build();
            ExecResult result = handle.start().waitForFinish();

            int exitValue = result.getExitValue();
            if (exitValue == 0) {
                String output = new String(baos.toByteArray());
                LOGGER.debug("Output from '{} -v {}", gppBinary.getPath(), output);    
                return output;                
            } else {
                LOGGER.warn("Executing '{} -v' return exit code {}, cannot use", gppBinary.getPath(), exitValue);
                return null;
            }
        }
    }

    public String transform(File gppBinary) {
        String output = outputProducer.transform(gppBinary);
        return output == null ? null : outputScraper.transform(output);
    }
}
