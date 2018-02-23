/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.jvm.toolchain.internal;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.JavaExecAction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.util.EnumMap;

public class JavaInstallationProbe {
    public static final String UNKNOWN = "unknown";

    private final LoadingCache<File, EnumMap<SysProp, String>> cache = CacheBuilder.newBuilder().build(new CacheLoader<File, EnumMap<SysProp, String>>() {
        @Override
        public EnumMap<SysProp, String> load(File javaHome) throws Exception {
            return getMetadataInternal(javaHome);
        }
    });

    private final ExecActionFactory factory;

    private enum SysProp {
        VERSION("java.version"),
        VENDOR("java.vendor"),
        ARCH("os.arch"),
        VM("java.vm.name"),
        VM_VERSION("java.vm.version"),
        RUNTIME("java.runtime.name"),
        Z_ERROR("Internal"); // This line MUST be last!

        private final String sysProp;

        SysProp(String sysProp) {
            this.sysProp = sysProp;
        }

    }

    public static class ProbeResult {
        private final EnumMap<SysProp, String> metadata;
        private final InstallType installType;
        private final String error;

        public static ProbeResult success(InstallType installType, EnumMap<SysProp, String> metadata) {
            return new ProbeResult(installType, metadata, null);
        }

        public static ProbeResult failure(InstallType installType, String error) {
            return new ProbeResult(installType, null, error);
        }

        private ProbeResult(InstallType installType, EnumMap<SysProp, String> metadata, String error) {
            this.installType = installType;
            this.metadata = metadata;
            this.error = error;
        }

        public InstallType getInstallType() {
            return installType;
        }

        public String getError() {
            return error;
        }

        public void configure(LocalJavaInstallation install) {
            JavaVersion javaVersion = JavaVersion.toVersion(metadata.get(SysProp.VERSION));
            install.setJavaVersion(javaVersion);
            String jdkName = computeJdkName(installType, metadata);
            install.setDisplayName(jdkName + " " + javaVersion.getMajorVersion());
        }
    }

    public enum InstallType {
        IS_JDK,
        IS_JRE,
        NO_SUCH_DIRECTORY,
        INVALID_JDK;
    }

    public JavaInstallationProbe(ExecActionFactory factory) {
        this.factory = factory;
    }

    public void current(LocalJavaInstallation currentJava) {
        ProbeResult.success(InstallType.IS_JDK, current()).configure(currentJava);
    }

    public ProbeResult checkJdk(File jdkPath) {
        if (!jdkPath.exists()) {
            return ProbeResult.failure(InstallType.NO_SUCH_DIRECTORY, "No such directory: " + jdkPath);
        }
        EnumMap<SysProp, String> metadata = cache.getUnchecked(jdkPath);
        String version = metadata.get(SysProp.VERSION);
        if (UNKNOWN.equals(version)) {
            return ProbeResult.failure(InstallType.INVALID_JDK, metadata.get(SysProp.Z_ERROR));
        }
        try {
            JavaVersion.toVersion(version);
        } catch (IllegalArgumentException ex) {
            // if the version string cannot be parsed
            return ProbeResult.failure(InstallType.INVALID_JDK, "Cannot parse version number: " + version);
        }
        if (javaExe(jdkPath, "javac").exists()) {
            return ProbeResult.success(InstallType.IS_JDK, metadata);
        }
        return ProbeResult.success(InstallType.IS_JRE, metadata);
    }

    private EnumMap<SysProp, String> getMetadataInternal(File jdkPath) {
        JavaExecAction exec = factory.newJavaExecAction();
        exec.executable(javaExe(jdkPath, "java"));
        File workingDir = Files.createTempDir();
        exec.setWorkingDir(workingDir);
        exec.setClasspath(new SimpleFileCollection(workingDir));
        try {
            writeProbe(workingDir);
            exec.setMain(JavaProbe.CLASSNAME);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exec.setStandardOutput(baos);
            ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
            exec.setErrorOutput(errorOutput);
            exec.setIgnoreExitValue(true);
            ExecResult result = exec.execute();
            int exitValue = result.getExitValue();
            if (exitValue == 0) {
                return parseExecOutput(baos.toString());
            }
            return error("Command returned unexpected result code: " + exitValue + "\nError output:\n" + errorOutput);
        } catch (ExecException ex) {
            return error(ex.getMessage());
        } finally {
            try {
                FileUtils.deleteDirectory(workingDir);
            } catch (IOException e) {
                throw new GradleException("Unable to delete temp directory", e);
            }
        }
    }

    private static String computeJdkName(InstallType result, EnumMap<SysProp, String> metadata) {
        String basename = result == InstallType.IS_JDK ? "JDK" : "JRE";
        String vendor = metadata.get(JavaInstallationProbe.SysProp.VENDOR);
        if (vendor == null) {
            return basename;
        } else {
            vendor = vendor.toLowerCase();
        }
        if (vendor.contains("apple")) {
            return "Apple " + basename;
        } else if (vendor.contains("oracle") || vendor.contains("sun")) {
            String vm = metadata.get(JavaInstallationProbe.SysProp.VM);
            if (vm != null && vm.contains("OpenJDK")) {
                return result == InstallType.IS_JDK ? "OpenJDK" : "OpenJDK JRE";
            }
            return "Oracle " + basename;
        } else if (vendor.contains("ibm")) {
            return "IBM " + basename;
        } else if (vendor.contains("azul systems")) {
            return "Zulu " + basename;
        } else if (vendor.contains("hewlett-packard")) {
            return "HP-UX " + basename;
        }

        return basename;
    }

    private static void writeProbe(File workingDir) {
        File probeFile = new File(workingDir, JavaProbe.CLASSNAME + ".class");
        try {
            IoActions.withResource(new FileOutputStream(probeFile), new ErroringAction<FileOutputStream>() {
                @Override
                protected void doExecute(FileOutputStream thing) throws Exception {
                    thing.write(JavaProbe.dump());
                }
            });
        } catch (FileNotFoundException e) {
            throw new GradleException("Unable to write Java probe file", e);
        }
    }

    private static File javaExe(File jdkPath, String command) {
        return new File(new File(jdkPath, "bin"), OperatingSystem.current().getExecutableName(command));
    }

    /**
     * This is the ASM version of a probe class that is the equivalent of the following source code:
     *
     * <code> public static class Probe { public static void main(String[] args) { System.out.println(System.getProperty("java.version", "unknown"));
     * System.out.println(System.getProperty("java.vendor", "unknown")); } } </code>
     *
     * We're using ASM because we need to generate a class which bytecode level is compatible with the lowest JDK version supported (1.1), while being practical to add to classpath when executing the
     * probe. You can add new system properties to be probed just by changing the {@link SysProp} enum.
     */
    private static class JavaProbe implements Opcodes {

        public static final String CLASSNAME = "JavaProbe";

        public static byte[] dump() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            createClassHeader(cw);
            createConstructor(cw);
            createMainMethod(cw);

            cw.visitEnd();

            return cw.toByteArray();
        }

        private static void createClassHeader(ClassWriter cw) {
            cw.visit(V1_1, ACC_PUBLIC + ACC_SUPER, CLASSNAME, null, "java/lang/Object", null);
        }

        private static void createMainMethod(ClassWriter cw) {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            for (SysProp type : SysProp.values()) {
                if (type != SysProp.Z_ERROR) {
                    dumpProperty(mv, type.sysProp);
                }
            }
            mv.visitInsn(RETURN);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l3, 0);
            mv.visitMaxs(3, 1);
            mv.visitEnd();
        }

        private static void dumpProperty(MethodVisitor mv, String property) {

            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn(property);
            mv.visitLdcInsn(UNKNOWN);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

        }

        private static void createConstructor(ClassWriter cw) {
            MethodVisitor mv;
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("this", "LJavaProbe;", null, l0, l1, 0);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
    }

    private static EnumMap<SysProp, String> parseExecOutput(String probeResult) {
        String[] split = probeResult.split(System.getProperty("line.separator"));
        if (split.length != SysProp.values().length - 1) { // -1 because of Z_ERROR
            return error("Unexpected command output: \n" + probeResult);
        }
        EnumMap<SysProp, String> result = new EnumMap<SysProp, String>(SysProp.class);
        for (SysProp type : SysProp.values()) {
            if (type != SysProp.Z_ERROR) {
                result.put(type, split[type.ordinal()]);
            }
        }
        return result;
    }

    private static EnumMap<SysProp, String> error(String message) {
        EnumMap<SysProp, String> result = new EnumMap<SysProp, String>(SysProp.class);
        for (SysProp type : SysProp.values()) {
            result.put(type, UNKNOWN);
        }
        result.put(SysProp.Z_ERROR, message);
        return result;
    }

    private static EnumMap<SysProp, String> current() {
        EnumMap<SysProp, String> result = new EnumMap<SysProp, String>(SysProp.class);
        for (SysProp type : SysProp.values()) {
            result.put(type, System.getProperty(type.sysProp, UNKNOWN));
        }
        return result;
    }
}
