package org.gradle.util.exec;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;

import java.io.File;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleBuilder {

    private File execDirectory;
    private String execCommand;
    private final List<String> arguments = new ArrayList<String>();
    private Map<String, String> environment = new HashMap<String, String>();
    private long keepWaitingTimeout = 100;
    private ExecOutputHandle standardOutputHandle;
    private ExecOutputHandle errorOutputHandle;
    private List<ExecHandleListener> listeners = new ArrayList<ExecHandleListener>();

    public ExecHandleBuilder() {
        this(false);
    }

    public ExecHandleBuilder(boolean outputDirectFlush) {
        standardOutputHandle = new StreamWriterExecOutputHandle(System.out, outputDirectFlush);
        errorOutputHandle = new StreamWriterExecOutputHandle(System.err, outputDirectFlush);
    }

    public ExecHandleBuilder(File execDirectory) {
        setExecDirectory(execDirectory);
    }

    public ExecHandleBuilder(String execCommand) {
        setExecCommand(execCommand);
    }

    public ExecHandleBuilder(File execDirectory, String execCommand) {
        setExecDirectory(execDirectory);
        setExecCommand(execCommand);
    }

    private void setExecDirectory(File execDirectory) {
        if ( execDirectory == null )
            throw new IllegalArgumentException("execDirectory == null!");
        if ( execDirectory.exists () && execDirectory.isFile() )
            throw new IllegalArgumentException("execDirectory is a file!");    
        this.execDirectory = execDirectory;
    }

    public ExecHandleBuilder execDirectory(File execDirectory) {
        setExecDirectory(execDirectory);
        return this;
    }

    private File getExecDirectory() {
        if ( execDirectory == null )
            return new File("."); // current directory
        return execDirectory;
    }

    private void setExecCommand(String execCommand) {
        if ( StringUtils.isEmpty(execCommand) )
            throw new IllegalArgumentException("execCommand == null!");
        this.execCommand = execCommand;
    }

    public ExecHandleBuilder execCommand(String execCommand) {
        setExecCommand(execCommand);
        return this;
    }

    public ExecHandleBuilder clearArguments() {
        this.arguments.clear();
        return this;
    }

    public ExecHandleBuilder arguments(String ... arguments) {
        if ( arguments == null ) throw new IllegalArgumentException("arguments == null!");
        this.arguments.addAll(Arrays.asList(arguments));
        return this;
    }

    public ExecHandleBuilder prependedArguments(String prefix, List<String> arguments) {
        if ( arguments == null ) throw new IllegalArgumentException("arguments == null!");
        for ( String argument : arguments ) {
            arguments.add(prefix + argument);
        }
        return this;
    }

    public ExecHandleBuilder environment(String key, String value) {
        environment.put(key, value);
        return this;
    }

    public ExecHandleBuilder clearEnvironment() {
        this.environment.clear();
        return this;
    }

    public ExecHandleBuilder inheritEnvironment() {
        clearEnvironment();
        environment.putAll(System.getenv());
        return this;
    }

    public ExecHandleBuilder inheritEnvironmentWithKeys(String ... keys) {
        if ( keys == null ) throw new IllegalArgumentException("keys == null!");
        if ( keys.length == 0 ) throw new IllegalArgumentException("keys.length == 0!");

        clearEnvironment();

        final Map<String, String> currentEnvironment = System.getenv();
        for ( final String key : Arrays.asList(keys)) {
            environment(key, currentEnvironment.get(key));
        }

        return this;
    }

    public ExecHandleBuilder inheritEnvironmentWithoutKeys(String ... keys) {
        if ( keys == null ) throw new IllegalArgumentException("keys == null!");
        if ( keys.length == 0 ) throw new IllegalArgumentException("keys.length == 0!");

        inheritEnvironment();
        for ( final String key : Arrays.asList(keys)) {
            environment.remove(key);
        }

        return this;
    }

    public ExecHandleBuilder keepWaitingTimeout(long keepWaitingTimeout) {
        if ( keepWaitingTimeout <= 0 ) throw new IllegalArgumentException("keepWaitingTimeout <= 0!");
        this.keepWaitingTimeout = keepWaitingTimeout;
        return this;
    }

    public ExecHandleBuilder standardOutputHandle(ExecOutputHandle standardOutputHandle) {
        if ( standardOutputHandle == null ) throw new IllegalArgumentException("standardOutputHandle == null!");
        this.standardOutputHandle = standardOutputHandle;
        return this;
    }

    public ExecHandleBuilder errorOutputHandle(ExecOutputHandle errorOutputHandle) {
        if ( errorOutputHandle == null ) throw new IllegalArgumentException("errorOutputHandle == null!");
        this.errorOutputHandle = errorOutputHandle;
        return this;
    }

    public ExecHandleBuilder clearListeners() {
        this.listeners.clear();
        return this;
    }

    public ExecHandleBuilder listeners(ExecHandleListener ... listeners) {
        if ( listeners == null ) throw new IllegalArgumentException("listeners == null!");
        this.listeners.addAll(Arrays.asList(listeners));
        return this;
    }

    public ExecHandle getExecHandle() {
        if ( StringUtils.isEmpty(execCommand) )
            throw new IllegalStateException("execCommand == null!");

        return new DefaultExecHandle(
                execDirectory,
                execCommand,
                arguments,
                environment,
                keepWaitingTimeout,
                standardOutputHandle,
                errorOutputHandle,
                listeners);
    }

    public ExecHandleBuilder arguments(List<String> arguments) {
        this.arguments.addAll(arguments);
        return this;
    }
}
