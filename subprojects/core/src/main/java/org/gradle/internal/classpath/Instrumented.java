/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.codehaus.groovy.runtime.ProcessGroovyMethods;
import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.codehaus.groovy.runtime.wrappers.Wrapper;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.SystemProperties;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Instrumented {
    private static final Listener NO_OP = new Listener() {
        @Override
        public void systemPropertyQueried(String key, @Nullable Object value, String consumer) {
        }

        @Override
        public void systemPropertyChanged(Object key, @Nullable Object value, String consumer) {
        }

        @Override
        public void systemPropertyRemoved(Object key, String consumer) {
        }

        @Override
        public void systemPropertiesCleared(String consumer) {
        }

        @Override
        public void envVariableQueried(String key, @Nullable String value, String consumer) {
        }

        @Override
        public void externalProcessStarted(String command, String consumer) {
        }

        @Override
        public void fileOpened(File file, String consumer) {
        }

        @Override
        public void fileCollectionObserved(FileCollection fileCollection, String consumer) {
        }
    };

    private static final AtomicReference<Listener> LISTENER = new AtomicReference<>(NO_OP);

    public static void setListener(Listener listener) {
        LISTENER.set(listener);
    }

    public static void discardListener() {
        setListener(NO_OP);
    }

    // Called by generated code
    @SuppressWarnings("unused")
    public static void groovyCallSites(CallSiteArray array) {
        for (CallSite callSite : array.array) {
            switch (callSite.getName()) {
                case "getProperty":
                    array.array[callSite.getIndex()] = new SystemPropertyCallSite(callSite);
                    break;
                case "setProperty":
                    array.array[callSite.getIndex()] = new SetSystemPropertyCallSite(callSite);
                    break;
                case "setProperties":
                    array.array[callSite.getIndex()] = new SetSystemPropertiesCallSite(callSite);
                    break;
                case "clearProperty":
                    array.array[callSite.getIndex()] = new ClearSystemPropertyCallSite(callSite);
                    break;
                case "properties":
                case "getProperties":
                    array.array[callSite.getIndex()] = new SystemPropertiesCallSite(callSite);
                    break;
                case "getInteger":
                    array.array[callSite.getIndex()] = new IntegerSystemPropertyCallSite(callSite);
                    break;
                case "getLong":
                    array.array[callSite.getIndex()] = new LongSystemPropertyCallSite(callSite);
                    break;
                case "getBoolean":
                    array.array[callSite.getIndex()] = new BooleanSystemPropertyCallSite(callSite);
                    break;
                case "getenv":
                    array.array[callSite.getIndex()] = new GetEnvCallSite(callSite);
                    break;
                case "exec":
                    array.array[callSite.getIndex()] = new ExecCallSite(callSite);
                    break;
                case "execute":
                    array.array[callSite.getIndex()] = new ExecuteCallSite(callSite);
                    break;
                case "start":
                    array.array[callSite.getIndex()] = new ProcessBuilderStartCallSite(callSite);
                    break;
                case "startPipeline":
                    array.array[callSite.getIndex()] = new ProcessBuilderStartPipelineCallSite(callSite);
                    break;
                case "<$constructor$>":
                    array.array[callSite.getIndex()] = new FileInputStreamConstructorCallSite(callSite);
                    break;
            }
        }
    }

    // Called by generated code.
    public static String systemProperty(String key, String consumer) {
        return systemProperty(key, null, consumer);
    }

    // Called by generated code.
    public static String systemProperty(String key, @Nullable String defaultValue, String consumer) {
        String value = System.getProperty(key);
        systemPropertyQueried(key, value, consumer);
        return value == null ? defaultValue : value;
    }

    // Called by generated code.
    public static Properties systemProperties(String consumer) {
        return new AccessTrackingProperties(System.getProperties(), new AccessTrackingProperties.Listener() {
            // Do not track accesses to non-String properties. Only String properties can be set externally, so they cannot affect the cached configuration.
            @Override
            public void onAccess(Object key, @Nullable Object value) {
                if (key instanceof String && (value == null || value instanceof String)) {
                    systemPropertyQueried(convertToString(key), convertToString(value), consumer);
                }
            }

            @Override
            public void onChange(Object key, Object newValue) {
                listener().systemPropertyChanged(key, newValue, consumer);
            }

            @Override
            public void onRemove(Object key) {
                listener().systemPropertyRemoved(key, consumer);
            }

            @Override
            public void onClear() {
                listener().systemPropertiesCleared(consumer);
            }
        });
    }

    // Called by generated code.
    public static String setSystemProperty(String key, String value, String consumer) {
        String oldValue = System.setProperty(key, value);
        systemPropertyQueried(key, oldValue, consumer);
        listener().systemPropertyChanged(key, value, consumer);
        return oldValue;
    }

    // Called by generated code.
    public static String clearSystemProperty(String key, String consumer) {
        String oldValue = System.clearProperty(key);
        systemPropertyQueried(key, oldValue, consumer);
        listener().systemPropertyRemoved(key, consumer);
        return oldValue;
    }

    public static void setSystemProperties(Properties properties, String consumer) {
        listener().systemPropertiesCleared(consumer);
        properties.forEach((k, v) -> listener().systemPropertyChanged(k, v, consumer));
        System.setProperties(properties);
    }

    // Called by generated code.
    public static Integer getInteger(String key, String consumer) {
        systemPropertyQueried(key, consumer);
        return Integer.getInteger(key);
    }

    // Called by generated code.
    public static Integer getInteger(String key, int defaultValue, String consumer) {
        systemPropertyQueried(key, consumer);
        return Integer.getInteger(key, defaultValue);
    }

    // Called by generated code.
    public static Integer getInteger(String key, Integer defaultValue, String consumer) {
        systemPropertyQueried(key, consumer);
        return Integer.getInteger(key, defaultValue);
    }

    // Called by generated code.
    public static Long getLong(String key, String consumer) {
        systemPropertyQueried(key, consumer);
        return Long.getLong(key);
    }

    // Called by generated code.
    public static Long getLong(String key, long defaultValue, String consumer) {
        systemPropertyQueried(key, consumer);
        return Long.getLong(key, defaultValue);
    }

    // Called by generated code.
    public static Long getLong(String key, Long defaultValue, String consumer) {
        systemPropertyQueried(key, consumer);
        return Long.getLong(key, defaultValue);
    }

    // Called by generated code.
    public static boolean getBoolean(String key, String consumer) {
        systemPropertyQueried(key, consumer);
        return Boolean.getBoolean(key);
    }

    // Called by generated code.
    public static String getenv(String key, String consumer) {
        String value = System.getenv(key);
        envVariableQueried(key, value, consumer);
        return value;
    }

    // Called by generated code.
    public static Map<String, String> getenv(String consumer) {
        return new AccessTrackingEnvMap((key, value) -> envVariableQueried(convertToString(key), value, consumer));
    }

    public static Process exec(Runtime runtime, String command, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return runtime.exec(command);
    }

    public static Process exec(Runtime runtime, String[] command, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return runtime.exec(command);
    }

    public static Process exec(Runtime runtime, String command, String[] envp, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return runtime.exec(command, envp);
    }

    public static Process exec(Runtime runtime, String[] command, String[] envp, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return runtime.exec(command, envp);
    }

    public static Process exec(Runtime runtime, String command, String[] envp, File dir, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return runtime.exec(command, envp, dir);
    }

    public static Process exec(Runtime runtime, String[] command, String[] envp, File dir, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return runtime.exec(command, envp, dir);
    }

    public static Process execute(String command, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command);
    }

    public static Process execute(String[] command, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command);
    }

    public static Process execute(List<?> command, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command);
    }

    public static Process execute(String command, String[] envp, File dir, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command, envp, dir);
    }

    public static Process execute(String command, List<?> envp, File dir, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command, envp, dir);
    }

    public static Process execute(String[] command, String[] envp, File dir, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command, envp, dir);
    }

    public static Process execute(String[] command, List<?> envp, File dir, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command, envp, dir);
    }

    public static Process execute(List<?> command, String[] envp, File dir, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command, envp, dir);
    }

    public static Process execute(List<?> command, List<?> envp, File dir, String consumer) throws IOException {
        externalProcessStarted(command, consumer);
        return ProcessGroovyMethods.execute(command, envp, dir);
    }

    public static Process start(ProcessBuilder builder, String consumer) throws IOException {
        externalProcessStarted(builder.command(), consumer);
        return builder.start();
    }

    @SuppressWarnings("unchecked")
    public static List<Process> startPipeline(List<ProcessBuilder> pipeline, String consumer) throws IOException {
        try {
            for (ProcessBuilder builder : pipeline) {
                externalProcessStarted(builder.command(), consumer);
            }
            Object result = ProcessBuilder.class.getMethod("startPipeline", List.class).invoke(null, pipeline);
            return (List<Process>) result;
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new NoSuchMethodError("Cannot find method ProcessBuilder.startPipeline");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new RuntimeException("Unexpected exception thrown by ProcessBuilder.startPipeline", e);
            }
        }
    }

    public static void fileCollectionObserved(FileCollection fileCollection, String consumer) {
        listener().fileCollectionObserved(fileCollection, consumer);
    }

    public static void fileOpened(File file, String consumer) {
        listener().fileOpened(absoluteFileOf(file), consumer);
    }

    private static File absoluteFileOf(File file) {
        return file.isAbsolute() ? file : new File(currentDir(), file.getPath());
    }

    private static File currentDir() {
        return SystemProperties.getInstance().getCurrentDir();
    }

    public static void fileOpened(String path, String consumer) {
        fileOpened(new File(path), consumer);
    }

    private static void envVariableQueried(String key, String value, String consumer) {
        listener().envVariableQueried(key, value, consumer);
    }

    private static void systemPropertyQueried(String key, String consumer) {
        systemPropertyQueried(key, System.getProperty(key), consumer);
    }

    private static void systemPropertyQueried(String key, @Nullable String value, String consumer) {
        listener().systemPropertyQueried(key, value, consumer);
    }

    private static void externalProcessStarted(String command, String consumer) {
        listener().externalProcessStarted(command, consumer);
    }

    private static void externalProcessStarted(String[] command, String consumer) {
        externalProcessStarted(joinCommand(command), consumer);
    }

    private static void externalProcessStarted(List<?> command, String consumer) {
        externalProcessStarted(joinCommand(command), consumer);
    }

    private static Listener listener() {
        return LISTENER.get();
    }

    private static Object unwrap(Object obj) {
        if (obj instanceof Wrapper) {
            return ((Wrapper) obj).unwrap();
        }
        return obj;
    }

    private static String convertToString(Object arg) {
        if (arg instanceof CharSequence) {
            return ((CharSequence) arg).toString();
        }
        return (String) arg;
    }

    private static String joinCommand(String[] command) {
        return String.join(" ", command);
    }

    private static String joinCommand(List<?> command) {
        return command.stream().map(String::valueOf).collect(Collectors.joining(" "));
    }

    public interface Listener {
        /**
         * Invoked when the code reads the system property with the String key.
         *
         * @param key the name of the property
         * @param value the value of the property at the time of reading or {@code null} if the property is not present
         * @param consumer the name of the class that is reading the property value
         */
        void systemPropertyQueried(String key, @Nullable Object value, String consumer);

        /**
         * Invoked when the code updates or adds the system property.
         *
         * @param key the name of the property, can be non-string
         * @param value the new value of the property, can be {@code null} or non-string
         * @param consumer the name of the class that is updating the property value
         */
        void systemPropertyChanged(Object key, @Nullable Object value, String consumer);

        /**
         * Invoked when the code removes the system property. The property may not be present.
         *
         * @param key the name of the property, can be non-string
         * @param consumer the name of the class that is removing the property value
         */
        void systemPropertyRemoved(Object key, String consumer);

        /**
         * Invoked when all system properties are removed.
         *
         * @param consumer the name of the class that is removing the system properties
         */
        void systemPropertiesCleared(String consumer);

        /**
         * Invoked when the code reads the environment variable.
         *
         * @param key the name of the variable
         * @param value the value of the variable
         * @param consumer the name of the class that is reading the variable
         */
        void envVariableQueried(String key, @Nullable String value, String consumer);

        /**
         * Invoked when the code starts an external process. The command string with all argument is provided for reporting but its value may not be suitable to actually invoke the command because all
         * arguments are joined together (separated by space) and there is no escaping of special characters.
         *
         * @param command the command used to start the process (with arguments)
         * @param consumer the name of the class that is starting the process
         */
        void externalProcessStarted(String command, String consumer);

        /**
         * Invoked when the code opens a file.
         *
         * @param file the absolute file that was open
         * @param consumer the name of the class that is opening the file
         */
        void fileOpened(File file, String consumer);

        /**
         * Invoked when configuration logic observes the given file collection.
         */
        void fileCollectionObserved(FileCollection inputs, String consumer);
    }

    private static class IntegerSystemPropertyCallSite extends AbstractCallSite {
        public IntegerSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Integer.class)) {
                return getInteger(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(Integer.class)) {
                return getInteger(arg1.toString(), (Integer) unwrap(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }
    }

    private static class LongSystemPropertyCallSite extends AbstractCallSite {
        public LongSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Long.class)) {
                return getLong(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(Long.class)) {
                return getLong(arg1.toString(), (Long) unwrap(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }
    }

    private static class BooleanSystemPropertyCallSite extends AbstractCallSite {
        public BooleanSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(Boolean.class)) {
                return getBoolean(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }
    }

    private static class SystemPropertyCallSite extends AbstractCallSite {
        public SystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty(arg.toString(), array.owner.getName());
            } else {
                return super.call(receiver, arg);
            }
        }

        @Override
        public Object callStatic(Class receiver, Object arg1) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty(arg1.toString(), array.owner.getName());
            } else {
                return super.callStatic(receiver, arg1);
            }
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty(arg1.toString(), convertToString(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }

        @Override
        public Object callStatic(Class receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperty(arg1.toString(), convertToString(arg2), array.owner.getName());
            } else {
                return super.callStatic(receiver, arg1, arg2);
            }
        }
    }

    private static class SetSystemPropertyCallSite extends AbstractCallSite {
        public SetSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(System.class)) {
                return setSystemProperty(convertToString(arg1), convertToString(arg2), array.owner.getName());
            } else {
                return super.call(receiver, arg1, arg2);
            }
        }

        @Override
        public Object callStatic(Class receiver, Object arg1, Object arg2) throws Throwable {
            if (receiver.equals(System.class)) {
                return setSystemProperty(convertToString(arg1), convertToString(arg2), array.owner.getName());
            } else {
                return super.callStatic(receiver, arg1, arg2);
            }
        }
    }

    private static class SetSystemPropertiesCallSite extends AbstractCallSite {
        public SetSystemPropertiesCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg1) throws Throwable {
            if (receiver.equals(System.class) && arg1 instanceof Properties) {
                setSystemProperties((Properties) arg1, array.owner.getName());
                return null;
            } else {
                return super.call(receiver, arg1);
            }
        }

        @Override
        public Object callStatic(Class receiver, Object arg1) throws Throwable {
            if (receiver.equals(System.class) && arg1 instanceof Properties) {
                setSystemProperties((Properties) arg1, array.owner.getName());
                return null;
            } else {
                return super.callStatic(receiver, arg1);
            }
        }
    }

    private static class ClearSystemPropertyCallSite extends AbstractCallSite {
        public ClearSystemPropertyCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object call(Object receiver, Object arg1) throws Throwable {
            if (receiver.equals(System.class)) {
                return clearSystemProperty(convertToString(arg1), array.owner.getName());
            } else {
                return super.call(receiver, arg1);
            }
        }

        @Override
        public Object callStatic(Class receiver, Object arg1) throws Throwable {
            if (receiver.equals(System.class)) {
                return clearSystemProperty(convertToString(arg1), array.owner.getName());
            } else {
                return super.callStatic(receiver, arg1);
            }
        }
    }

    private static class SystemPropertiesCallSite extends AbstractCallSite {
        public SystemPropertiesCallSite(CallSite callSite) {
            super(callSite);
        }

        @Override
        public Object callGetProperty(Object receiver) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperties(array.owner.getName());
            } else {
                return super.callGetProperty(receiver);
            }
        }

        @Override
        public Object call(Object receiver) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperties(array.owner.getName());
            } else {
                return super.call(receiver);
            }
        }

        @Override
        public Object callStatic(Class receiver) throws Throwable {
            if (receiver.equals(System.class)) {
                return systemProperties(array.owner.getName());
            } else {
                return super.callStatic(receiver);
            }
        }
    }

    private static class GetEnvCallSite extends AbstractCallSite {
        public GetEnvCallSite(CallSite prev) {
            super(prev);
        }

        @Override
        public Object call(Object receiver) throws Throwable {
            if (receiver.equals(System.class)) {
                return getenv(array.owner.getName());
            }
            return super.call(receiver);
        }

        @Override
        public Object call(Object receiver, Object arg1) throws Throwable {
            if (receiver.equals(System.class) && arg1 instanceof CharSequence) {
                return getenv(convertToString(arg1), array.owner.getName());
            }
            return super.call(receiver, arg1);
        }
    }

    /**
     * The call site for {@code Runtime.exec}.
     */
    private static class ExecCallSite extends AbstractCallSite {
        public ExecCallSite(CallSite prev) {
            super(prev);
        }

        @Override
        public Object call(Object receiver, Object arg1) throws Throwable {
            Optional<Process> result = tryCallExec(receiver, arg1, null, null);
            if (result.isPresent()) {
                return result.get();
            }
            return super.call(receiver, arg1);
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2) throws Throwable {
            Optional<Process> result = tryCallExec(receiver, arg1, arg2, null);
            if (result.isPresent()) {
                return result.get();
            }
            return super.call(receiver, arg1, arg2);
        }

        @Override
        public Object call(Object receiver, Object arg1, Object arg2, Object arg3) throws Throwable {
            Optional<Process> result = tryCallExec(receiver, arg1, arg2, arg3);
            if (result.isPresent()) {
                return result.get();
            }
            return super.call(receiver, arg1, arg2, arg3);
        }

        private Optional<Process> tryCallExec(Object runtimeArg, Object commandArg, @Nullable Object envpArg, @Nullable Object fileArg) throws Throwable {
            runtimeArg = unwrap(runtimeArg);
            commandArg = unwrap(commandArg);
            envpArg = unwrap(envpArg);
            fileArg = unwrap(fileArg);

            if (runtimeArg instanceof Runtime) {
                Runtime runtime = (Runtime) runtimeArg;

                if (fileArg == null || fileArg instanceof File) {
                    File file = (File) fileArg;

                    if (envpArg == null || envpArg instanceof String[]) {
                        String[] envp = (String[]) envpArg;

                        if (commandArg instanceof CharSequence) {
                            String command = convertToString(commandArg);
                            return Optional.of(exec(runtime, command, envp, file, array.owner.getName()));
                        } else if (commandArg instanceof String[]) {
                            String[] command = (String[]) commandArg;
                            return Optional.of(exec(runtime, command, envp, file, array.owner.getName()));
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The call site for Groovy's {@code String.execute}, {@code String[].execute}, and {@code List.execute}. This also handles {@code ProcessGroovyMethods.execute}.
     */
    private static class ExecuteCallSite extends AbstractCallSite {
        public ExecuteCallSite(CallSite prev) {
            super(prev);
        }

        // String|String[]|List.execute()
        @Override
        public Object call(Object receiver) throws Throwable {
            Optional<Process> result = tryCallExecute(receiver, null, null);
            if (result.isPresent()) {
                return result.get();
            }
            return super.call(receiver);
        }

        // ProcessGroovyMethod.execute(String|String[]|List)
        @Override
        public Object call(Object receiver, Object arg1) throws Throwable {
            if (receiver.equals(ProcessGroovyMethods.class)) {
                Optional<Process> process = tryCallExecute(arg1, null, null);
                if (process.isPresent()) {
                    return process.get();
                }
            }
            return super.call(receiver, arg1);
        }

        // static import execute(String|String[]|List)
        @Override
        public Object callStatic(Class receiver, Object arg1) throws Throwable {
            if (receiver.equals(ProcessGroovyMethods.class)) {
                Optional<Process> process = tryCallExecute(arg1, null, null);
                if (process.isPresent()) {
                    return process.get();
                }
            }
            return super.callStatic(receiver, arg1);
        }

        // String|String[]|List.execute(String[]|List, File)
        @Override
        public Object call(Object receiver, @Nullable Object arg1, @Nullable Object arg2) throws Throwable {
            Optional<Process> result = tryCallExecute(receiver, arg1, arg2);
            if (result.isPresent()) {
                return result.get();
            }
            return super.call(receiver, arg1, arg2);
        }

        // ProcessGroovyMethod.execute(String|String[]|List, String[]|List, File)
        @Override
        public Object call(Object receiver, Object arg1, @Nullable Object arg2, @Nullable Object arg3) throws Throwable {
            if (receiver.equals(ProcessGroovyMethods.class)) {
                Optional<Process> result = tryCallExecute(arg1, arg2, arg3);
                if (result.isPresent()) {
                    return result.get();
                }
            }
            return super.call(receiver, arg1, arg2, arg3);
        }

        // static import execute(String|String[]|List, String[]|List, File)
        @Override
        public Object callStatic(Class receiver, Object arg1, @Nullable Object arg2, @Nullable Object arg3) throws Throwable {
            if (receiver.equals(ProcessGroovyMethods.class)) {
                Optional<Process> result = tryCallExecute(arg1, arg2, arg3);
                if (result.isPresent()) {
                    return result.get();
                }
            }
            return super.callStatic(receiver, arg1, arg2, arg3);
        }

        private Optional<Process> tryCallExecute(Object commandArg, @Nullable Object envpArg, @Nullable Object fileArg) throws Throwable {
            commandArg = unwrap(commandArg);
            envpArg = unwrap(envpArg);
            fileArg = unwrap(fileArg);

            if (fileArg == null || fileArg instanceof File) {
                File file = (File) fileArg;

                if (commandArg instanceof CharSequence) {
                    String command = convertToString(commandArg);

                    if (envpArg == null || envpArg instanceof String[]) {
                        return Optional.of(execute(command, (String[]) envpArg, file, array.owner.getName()));
                    } else if (envpArg instanceof List) {
                        return Optional.of(execute(command, (List<?>) envpArg, file, array.owner.getName()));
                    }
                } else if (commandArg instanceof String[]) {
                    String[] command = (String[]) commandArg;

                    if (envpArg == null || envpArg instanceof String[]) {
                        return Optional.of(execute(command, (String[]) envpArg, file, array.owner.getName()));
                    } else if (envpArg instanceof List) {
                        return Optional.of(execute(command, (List<?>) envpArg, file, array.owner.getName()));
                    }
                } else if (commandArg instanceof List) {
                    List<?> command = (List<?>) commandArg;

                    if (envpArg == null || envpArg instanceof String[]) {
                        return Optional.of(execute(command, (String[]) envpArg, file, array.owner.getName()));
                    } else if (envpArg instanceof List) {
                        return Optional.of(execute(command, (List<?>) envpArg, file, array.owner.getName()));
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The call site for {@code ProcessBuilder.start}.
     */
    private static class ProcessBuilderStartCallSite extends AbstractCallSite {
        public ProcessBuilderStartCallSite(CallSite prev) {
            super(prev);
        }

        // ProcessBuilder.start()
        @Override
        public Object call(Object receiver) throws Throwable {
            if (receiver instanceof ProcessBuilder) {
                return start((ProcessBuilder) receiver, array.owner.getName());
            }
            return super.call(receiver);
        }
    }

    /**
     * The call site for {@code ProcessBuilder.start}.
     */
    private static class ProcessBuilderStartPipelineCallSite extends AbstractCallSite {
        public ProcessBuilderStartPipelineCallSite(CallSite prev) {
            super(prev);
        }

        // ProcessBuilder.startPipeline(List<ProcessBuilder> pbs)
        @SuppressWarnings("unchecked")
        @Override
        public Object call(Object receiver, Object arg1) throws Throwable {
            if (receiver.equals(ProcessBuilder.class) && arg1 instanceof List) {
                return startPipeline((List<ProcessBuilder>) arg1, array.owner.getName());
            }
            return super.call(receiver, arg1);
        }

        // ProcessBuilder.startPipeline(List<ProcessBuilder> pbs) with static import
        @SuppressWarnings("unchecked")
        @Override
        public Object callStatic(Class receiver, Object arg1) throws Throwable {
            if (receiver.equals(ProcessBuilder.class) && arg1 instanceof List) {
                return startPipeline((List<ProcessBuilder>) arg1, array.owner.getName());
            }
            return super.callStatic(receiver, arg1);
        }
    }

    private static class FileInputStreamConstructorCallSite extends AbstractCallSite {
        public FileInputStreamConstructorCallSite(CallSite prev) {
            super(prev);
        }

        @Override
        public Object callConstructor(Object receiver, Object arg1) throws Throwable {
            if (receiver.equals(FileInputStream.class)) {
                Object unwrappedArg1 = unwrap(arg1);
                if (unwrappedArg1 instanceof CharSequence) {
                    String path = convertToString(unwrappedArg1);
                    fileOpened(path, array.owner.getName());
                    return new FileInputStream(path);
                } else if (unwrappedArg1 instanceof File) {
                    File file = (File) unwrappedArg1;
                    fileOpened(file, array.owner.getName());
                    return new FileInputStream(file);
                }
            }

            return super.callConstructor(receiver, arg1);
        }
    }
}
