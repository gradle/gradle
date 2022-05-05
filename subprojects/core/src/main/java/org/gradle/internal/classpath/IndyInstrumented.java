/*
 * Copyright 2022 the original author or authors.
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
import org.codehaus.groovy.vmplugin.v8.CacheableCallSite;
import org.codehaus.groovy.vmplugin.v8.IndyInterface;
import org.gradle.api.GradleException;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class IndyInstrumented {

    private static final Map<String, CallInterceptor> METHOD_INTERCEPTORS = new HashMap<>();
    private static final Map<String, CallInterceptor> PROPERTY_INTERCEPTORS = new HashMap<>();
    private static final CallInterceptor CONSTRUCTOR_INTERCEPTOR = new ConstructorInterceptor();

    static {
        METHOD_INTERCEPTORS.put("getProperty", new SystemGetPropertyInterceptor());
        METHOD_INTERCEPTORS.put("setProperty", new SystemSetPropertyInterceptor());
        METHOD_INTERCEPTORS.put("setProperties", new SystemSetPropertiesInterceptor());
        METHOD_INTERCEPTORS.put("clearProperty", new SystemClearPropertyInterceptor());

        CallInterceptor getPropertiesInterceptor = new SystemGetPropertiesInterceptor();
        METHOD_INTERCEPTORS.put("getProperties", getPropertiesInterceptor);
        PROPERTY_INTERCEPTORS.put("properties", getPropertiesInterceptor);

        METHOD_INTERCEPTORS.put("getInteger", new IntegerGetIntegerInterceptor());
        METHOD_INTERCEPTORS.put("getLong", new LongGetLongInterceptor());
        METHOD_INTERCEPTORS.put("getBoolean", new BooleanGetBooleanInterceptor());
        METHOD_INTERCEPTORS.put("getenv", new SystemGetenvInterceptor());
        METHOD_INTERCEPTORS.put("exec", new RuntimeExecInterceptor());
        METHOD_INTERCEPTORS.put("execute", new ProcessGroovyMethodsExecuteInterceptor());
        METHOD_INTERCEPTORS.put("start", new ProcessBuilderStartInterceptor());
        METHOD_INTERCEPTORS.put("startPipeline", new ProcessBuilderStartPipelineInterceptor());
    }

    /**
     * The bootstrap method for method calls from Groovy compiled code with indy enabled.
     * Gradle's bytecode processor replaces the Groovy's original {@link IndyInterface#bootstrap(MethodHandles.Lookup, String, MethodType, String, int)}
     * with this method to intercept potentially "interesting" calls and do some additional work.
     *
     * @param caller the lookup for the caller (JVM-supplied)
     * @param callType the type of the call (corresponds to {@link IndyInterface.CallType} constant)
     * @param type the call site type
     * @param name the real method name
     * @param flags call flags
     * @return the produced CallSite
     * @see IndyInterface
     */
    public static CallSite bootstrap(MethodHandles.Lookup caller, String callType, MethodType type, String name, int flags) {
        CacheableCallSite cs = toGroovyCacheableCallSite(IndyInterface.bootstrap(caller, callType, type, name, flags));
        switch (callType) {
            case "invoke":
                maybeApplyInterceptor(cs, caller, flags, METHOD_INTERCEPTORS.get(name));
                break;
            case "getProperty":
                maybeApplyInterceptor(cs, caller, flags, PROPERTY_INTERCEPTORS.get(name));
                break;
            case "init":
                maybeApplyInterceptor(cs, caller, flags, CONSTRUCTOR_INTERCEPTOR);
                break;
        }
        return cs;
    }

    private static void maybeApplyInterceptor(CacheableCallSite cs, MethodHandles.Lookup caller, int flags, @Nullable CallInterceptor interceptor) {
        if (interceptor == null) {
            return;
        }
        MethodHandle defaultTarget = interceptor.decorate(cs.getDefaultTarget(), caller, flags);
        cs.setTarget(defaultTarget);
        cs.setDefaultTarget(defaultTarget);
        cs.setFallbackTarget(interceptor.decorate(cs.getFallbackTarget(), caller, flags));
    }

    private static CacheableCallSite toGroovyCacheableCallSite(CallSite cs) {
        if (!(cs instanceof CacheableCallSite)) {
            throw new GradleException("Groovy produced unrecognized callsite type of " + cs.getClass());
        }
        return (CacheableCallSite) cs;
    }

    private static class Call {
        private final MethodHandle original;
        private final Object[] args;
        private final boolean isSpread;

        public Call(MethodHandle original, Object[] args, boolean isSpread) {
            this.original = original;
            this.args = args;
            this.isSpread = isSpread;
        }

        public Object callOriginal() throws Throwable {
            // TODO(mlopatkin) Calling the original method may result in Groovy inlining the target method into a call site.
            //    This will break interception.
            //    Our current implementation based on Groovy CallSites suffers from the same problem though.
            return original.invokeExact(args);
        }

        public Object getReceiver() {
            return args[0];
        }

        public Object[] getArguments() {
            if (isSpread) {
                // Spread calls, e.g. `getProperty(*["foo", "bar"])`, get all their arguments folded into a single array.
                Object[] argsArray = (Object[]) args[1];
                return unwrap(argsArray, 0, argsArray.length);
            }
            return unwrap(args, 1, args.length - 1);
        }

        private static Object[] unwrap(Object[] array, int offset, int size) {
            Object[] result = new Object[size];
            for (int i = 0; i < size; ++i) {
                result[i] = Instrumented.unwrap(array[i + offset]);
            }
            return result;
        }
    }


    private static abstract class CallInterceptor {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

        private static final MethodHandle INTERCEPTOR;

        static {
            try {
                INTERCEPTOR = LOOKUP.findVirtual(CallInterceptor.class, "intercept", MethodType.methodType(Object.class, MethodHandle.class, int.class, String.class, Object[].class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new GradleException("Failed to set up an interceptor method", e);
            }
        }

        public MethodHandle decorate(MethodHandle original, MethodHandles.Lookup caller, int flags) {
            MethodHandle spreader = original.asSpreader(Object[].class, original.type().parameterCount());
            MethodHandle decorated = MethodHandles.insertArguments(INTERCEPTOR, 0, this, spreader, flags, caller.lookupClass().getName());
            return decorated.asCollector(Object[].class, original.type().parameterCount()).asType(original.type());
        }

        private Object intercept(MethodHandle original, int flags, String consumer, Object[] args) throws Throwable {
            boolean isSpread = (flags & IndyInterface.SPREAD_CALL) != 0;
            return doIntercept(consumer, new Call(original, args, isSpread));
        }

        public abstract Object doIntercept(String consumer, Call call) throws Throwable;
    }


    private static class SystemGetPropertyInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!System.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            switch (args.length) {
                case 1:
                    return Instrumented.systemProperty(Instrumented.convertToString(args[0]), consumer);
                case 2:
                    return Instrumented.systemProperty(Instrumented.convertToString(args[0]), Instrumented.convertToString(args[1]), consumer);
            }
            return call.callOriginal();
        }
    }

    private static class SystemSetPropertyInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!System.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            if (args.length == 2) {
                return Instrumented.setSystemProperty(Instrumented.convertToString(args[0]), Instrumented.convertToString(args[1]), consumer);
            }
            return call.callOriginal();
        }
    }

    private static class SystemSetPropertiesInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!System.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            if (args.length == 1) {
                Instrumented.setSystemProperties((Properties) args[0], consumer);
                return null;
            }
            return call.callOriginal();
        }
    }

    private static class SystemClearPropertyInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!System.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            if (args.length == 1) {
                return Instrumented.clearSystemProperty(Instrumented.convertToString(args[0]), consumer);
            }
            return call.callOriginal();
        }
    }

    private static class SystemGetPropertiesInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!System.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            if (args.length == 0) {
                return Instrumented.systemProperties(consumer);
            }
            return call.callOriginal();
        }
    }

    private static class IntegerGetIntegerInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!Integer.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            switch (args.length) {
                case 1:
                    return Instrumented.getInteger(Instrumented.convertToString(args[0]), consumer);
                case 2:
                    return Instrumented.getInteger(Instrumented.convertToString(args[0]), (Integer) args[1], consumer);
            }
            return call.callOriginal();
        }
    }

    private static class LongGetLongInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!Long.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            switch (args.length) {
                case 1:
                    return Instrumented.getLong(Instrumented.convertToString(args[0]), consumer);
                case 2:
                    return Instrumented.getLong(Instrumented.convertToString(args[0]), (Long) args[1], consumer);
            }
            return call.callOriginal();
        }
    }

    private static class BooleanGetBooleanInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!Boolean.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            if (args.length == 1) {
                return Instrumented.getBoolean(Instrumented.convertToString(args[0]), consumer);
            }
            return call.callOriginal();
        }
    }

    private static class SystemGetenvInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!System.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            switch (args.length) {
                case 0:
                    return Instrumented.getenv(consumer);
                case 1:
                    return Instrumented.getenv(Instrumented.convertToString(args[0]), consumer);
            }
            return call.callOriginal();
        }
    }

    private static class RuntimeExecInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!(call.getReceiver() instanceof Runtime)) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            if (1 <= args.length && args.length <= 3) {
                Object arg0 = args[0];
                Object optArg1 = getArgOrNull(1, args);
                Object optArg2 = getArgOrNull(2, args);

                Optional<Process> result = tryCallExec(call.getReceiver(), arg0, optArg1, optArg2, consumer);
                if (result.isPresent()) {
                    return result.get();
                }
            }
            return call.callOriginal();
        }

        private Optional<Process> tryCallExec(Object runtimeArg, Object commandArg, @Nullable Object envpArg, @Nullable Object fileArg, String consumer) throws Throwable {
            if (runtimeArg instanceof Runtime) {
                Runtime runtime = (Runtime) runtimeArg;

                if (fileArg == null || fileArg instanceof File) {
                    File file = (File) fileArg;

                    if (envpArg == null || envpArg instanceof String[]) {
                        String[] envp = (String[]) envpArg;

                        if (commandArg instanceof CharSequence) {
                            String command = Instrumented.convertToString(commandArg);
                            return Optional.of(Instrumented.exec(runtime, command, envp, file, consumer));
                        } else if (commandArg instanceof String[]) {
                            String[] command = (String[]) commandArg;
                            return Optional.of(Instrumented.exec(runtime, command, envp, file, consumer));
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    private static class ProcessGroovyMethodsExecuteInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            Object receiver = call.getReceiver();
            Object[] args = call.getArguments();
            boolean isStaticCall = receiver.equals(ProcessGroovyMethods.class);
            int argsOffset = isStaticCall ? 1 : 0;
            if (argsOffset <= args.length && args.length <= 2 + argsOffset) {
                Object commandArg = isStaticCall ? args[0] : receiver;
                Object optEnvpArg = getArgOrNull(argsOffset, args);
                Object optFileArg = getArgOrNull(argsOffset + 1, args);

                Optional<Process> result = tryCallExecute(commandArg, optEnvpArg, optFileArg, consumer);
                if (result.isPresent()) {
                    return result.get();
                }
            }
            return call.callOriginal();
        }

        private Optional<Process> tryCallExecute(Object commandArg, @Nullable Object envpArg, @Nullable Object fileArg, String consumer) throws Throwable {
            if (fileArg == null || fileArg instanceof File) {
                File file = (File) fileArg;

                if (commandArg instanceof CharSequence) {
                    String command = Instrumented.convertToString(commandArg);

                    if (envpArg == null || envpArg instanceof String[]) {
                        return Optional.of(Instrumented.execute(command, (String[]) envpArg, file, consumer));
                    } else if (envpArg instanceof List) {
                        return Optional.of(Instrumented.execute(command, (List<?>) envpArg, file, consumer));
                    }
                } else if (commandArg instanceof String[]) {
                    String[] command = (String[]) commandArg;

                    if (envpArg == null || envpArg instanceof String[]) {
                        return Optional.of(Instrumented.execute(command, (String[]) envpArg, file, consumer));
                    } else if (envpArg instanceof List) {
                        return Optional.of(Instrumented.execute(command, (List<?>) envpArg, file, consumer));
                    }
                } else if (commandArg instanceof List) {
                    List<?> command = (List<?>) commandArg;

                    if (envpArg == null || envpArg instanceof String[]) {
                        return Optional.of(Instrumented.execute(command, (String[]) envpArg, file, consumer));
                    } else if (envpArg instanceof List) {
                        return Optional.of(Instrumented.execute(command, (List<?>) envpArg, file, consumer));
                    }
                }
            }
            return Optional.empty();
        }
    }

    private static class ProcessBuilderStartInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!(call.getReceiver() instanceof ProcessBuilder)) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            if (args.length == 0) {
                return Instrumented.start((ProcessBuilder) call.getReceiver(), consumer);
            }
            return call.callOriginal();
        }
    }

    private static class ProcessBuilderStartPipelineInterceptor extends CallInterceptor {
        @SuppressWarnings("unchecked")
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!ProcessBuilder.class.equals(call.getReceiver())) {
                return call.callOriginal();
            }
            Object[] args = call.getArguments();
            if (args.length == 1) {
                return Instrumented.startPipeline((List<ProcessBuilder>) args[0], consumer);
            }
            return call.callOriginal();
        }
    }

    private static class ConstructorInterceptor extends CallInterceptor {
        @Override
        public Object doIntercept(String consumer, Call call) throws Throwable {
            if (!call.getReceiver().equals(FileInputStream.class)) {
                return call.callOriginal();
            }
            Object arg = call.getArguments()[0];
            if (arg instanceof CharSequence) {
                String path = Instrumented.convertToString(arg);
                Instrumented.fileOpened(path, consumer);
                return new FileInputStream(path);
            } else if (arg instanceof File) {
                File file = (File) arg;
                Instrumented.fileOpened(file, consumer);
                return new FileInputStream(file);
            }
            return call.callOriginal();
        }
    }

    private static @Nullable Object getArgOrNull(int index, Object[] args) {
        return (0 <= index && index < args.length) ? args[index] : null;
    }
}
