package org.gradle.logging;

public interface TerminalLoggingConfigurer {
    void configure(boolean stdOutIsTerminal, boolean stdErrIsTerminal);
}
