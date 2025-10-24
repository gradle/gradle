/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.tooling.internal.consumer.connection;

import org.gradle.internal.UncheckedException;
import org.gradle.tooling.internal.consumer.LoggingProvider;
import org.gradle.tooling.internal.consumer.OperationTxtLogger;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.build.Help;
import org.gradle.tooling.model.build.VersionBanner;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Intercepts {@code --help}/{@code --version}/{@code -V} arguments on the consumer side and handles them
 * by querying lightweight models from the provider and writing the output to the
 * configured streams, short-circuiting as per CLI semantics.
 */
public class VersionHelpConsumerActionExecutor implements ConsumerActionExecutor {
    private static final String MODEL_HELP = Help.class.getName();
    private static final String MODEL_VERSION = VersionBanner.class.getName();

    private final ConsumerActionExecutor delegate;

    public VersionHelpConsumerActionExecutor(ConsumerActionExecutor delegate, LoggingProvider loggingProvider) {
        this.delegate = delegate;
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public <T> T run(ConsumerAction<T> action) throws UnsupportedOperationException, IllegalStateException {
        ConsumerOperationParameters params = action.getParameters();
        List<String> args = params.getArguments();
        if (args == null || args.isEmpty()) {
            return delegate.run(action);
        }

        ParsedFlags flags = ParsedFlags.parse(args);
        if (!flags.hasAny()) {
            return delegate.run(action);
        }

        // When help or version requested (non -V): print and short-circuit build execution
        if (flags.help || flags.version) {
            OperationTxtLogger.log(params, "intercept flags=" + (flags.help ? "--help" : "--version"));
            boolean printed = flags.help ? printModel(MODEL_HELP, params) : printModel(MODEL_VERSION, params);
            if (printed) {
                OperationTxtLogger.log(params, "printed " + (flags.help ? "help" : "version") + " and short-circuited");
                // Do not run the underlying action/build
                return null;
            }
            // Older providers may not support these models. Fall back to normal execution.
            OperationTxtLogger.log(params, "model unavailable; delegating to provider");
            return delegate.run(action);
        }

        // For -V/--show-version: print and continue build, removing the flag from args
        if (flags.showVersion) {
            OperationTxtLogger.log(params, "intercept flags=--show-version/-V (print and continue)");
            // Best-effort: if model is unavailable (older provider), proceed without printing
            printModel(MODEL_VERSION, params);
            // continue by delegating with filtered args (no -V)
            ConsumerOperationParameters.Builder builder = ConsumerOperationParameters.builder();
            builder.copyFrom(params);
            // Preserve required metadata
            builder.setEntryPoint(params.getEntryPointName());
            builder.setArguments(flags.filteredArgs);
            final ConsumerOperationParameters filtered = builder.build();
            OperationTxtLogger.log(filtered, "continuing without -V; args=" + flags.filteredArgs);
            return delegate.run(new ConsumerAction<T>() {
                @Override
                public ConsumerOperationParameters getParameters() {
                    return filtered;
                }

                @Override
                public T run(ConsumerConnection connection) {
                    return action.run(connection);
                }
            });
        }

        return delegate.run(action);
    }

    private boolean printModel(String modelName, ConsumerOperationParameters params) {
        // Query the model from the provider by reusing the same connection through the delegate
        // We piggyback on the delegate's run by creating a small action that fetches the model via connection.run(Class,...)
        Object model;
        try {
            model = delegate.run(new ConsumerAction<Object>() {
            @Override
            public ConsumerOperationParameters getParameters() {
                return params;
            }

            @Override
            public Object run(ConsumerConnection connection) {
                try {
                    Class<?> clazz = Class.forName(modelName);
                    return connection.run(clazz, params);
                } catch (ClassNotFoundException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });
        } catch (RuntimeException ex) {
            // Model likely unsupported on older provider – signal caller to fall back
            OperationTxtLogger.log(params, "failed to acquire model=" + modelName + ": " + ex.getClass().getSimpleName());
            return false;
        }

        String text;
        if (model instanceof Help) {
            text = ((Help) model).getHelpOutput();
        } else if (model instanceof VersionBanner) {
            text = ((VersionBanner) model).getVersionOutput();
        } else {
            text = String.valueOf(model);
        }

        writeTo(params.getStandardOutput(), text);
        OperationTxtLogger.log(params, "printed model=" + modelName + ", size=" + (text == null ? 0 : text.length()));
        return true;
    }

    private static void writeTo(OutputStream out, String text) {
        if (out == null) {
            return;
        }
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            // Swallow to keep behavior similar to CLI where printing failures shouldn't crash the build setup
        }
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }

    private static class ParsedFlags {
        final boolean help;
        final boolean version;
        final boolean showVersion;
        final List<String> filteredArgs;

        private ParsedFlags(boolean help, boolean version, boolean showVersion, List<String> filteredArgs) {
            this.help = help;
            this.version = version;
            this.showVersion = showVersion;
            this.filteredArgs = filteredArgs;
        }

        boolean hasAny() {
            return help || version || showVersion;
        }

        static ParsedFlags parse(List<String> args) {
            boolean help = false;
            boolean version = false;
            boolean showVersion = false;
            List<String> filtered = new ArrayList<String>(args.size());
            for (String a : args) {
                if ("--help".equals(a) || "-h".equals(a) || "-?".equals(a)) {
                    help = true;
                } else if ("--version".equals(a) || "-v".equals(a)) {
                    version = true;
                } else if ("--show-version".equals(a) || "-V".equals(a)) {
                    showVersion = true;
                    // do not include in filtered
                } else {
                    filtered.add(a);
                }
            }
            // Precedence: help/version short-circuit; show-version only if neither help nor version present
            if (help || version) {
                showVersion = false;
            }
            return new ParsedFlags(help, version, showVersion, Collections.unmodifiableList(filtered));
        }
    }
}
