/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.security.internal.gnupg;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.plugins.signing.signatory.SignatorySupport;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Signatory that uses the gnupg cli program.
 *
 * @since 4.5
 */
public class GnupgSignatory extends SignatorySupport {

    private static final Logger LOG = Logging.getLogger(GnupgSignatory.class);

    private final ExecOperations execOperations;
    private final String name;

    private final String executable;
    private final boolean useLegacyGpg;
    private final File homeDir;
    private final File optionsFile;
    private final String keyName;
    private final String passphrase;

    interface Services {
        @Inject
        ExecOperations getExecOperations();
    }

    public GnupgSignatory(Project project, String name, GnupgSettings settings) {
        this.execOperations = project.getObjects().newInstance(Services.class).getExecOperations();
        this.name = name;
        this.executable = settings.getExecutable();
        this.useLegacyGpg = settings.getUseLegacyGpg();
        this.homeDir = settings.getHomeDir();
        this.optionsFile = settings.getOptionsFile();
        this.keyName = settings.getKeyName();
        this.passphrase = settings.getPassphrase();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void sign(final InputStream input, final OutputStream output) {
        final List<String> arguments = buildArgumentList();
        LOG.info("Invoking {} with arguments: {}", executable, arguments);
        execOperations.exec(spec -> {
                spec.setExecutable(executable);
                spec.setArgs(arguments);
                spec.setStandardInput(prepareStdin(input));
                spec.setStandardOutput(output);
            }
        );
    }

    private InputStream prepareStdin(InputStream input) {
        if (passphrase != null) {
            return new SequenceInputStream(new ByteArrayInputStream((passphrase + "\n").getBytes()), input);
        } else {
            return input;
        }
    }

    private List<String> buildArgumentList() {
        final List<String> args = new ArrayList<String>();
        if (homeDir != null) {
            args.add("--homedir");
            args.add(homeDir.getAbsolutePath());
        }
        if (optionsFile != null) {
            args.add("--options");
            args.add(optionsFile.getAbsolutePath());
        }
        if (keyName != null) {
            args.add("--local-user");
            args.add(keyName);
        }
        if (passphrase != null) {
            if (useLegacyGpg) {
                args.add("--no-use-agent");
            } else {
                args.add("--pinentry-mode=loopback");
            }
            args.add("--passphrase-fd");
            args.add("0");
        } else {
            if (useLegacyGpg) {
                args.add("--use-agent");
            }
        }
        args.add("--no-tty");
        args.add("--batch");
        args.add("--detach-sign");
        return args;
    }

    @Override
    public String getKeyId() {
        return keyName;
    }

}
