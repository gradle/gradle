
package gradle.advice;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.matcher.ElementMatchers;
import org.gradle.api.internal.file.collections.DirectoryFileTree;

public class DirectoryScanningInterceptor {
    public static void install() {
        ByteBuddyAgent.install();

        ClassLoader targetClassLoader = DirectoryFileTree.class.getClassLoader();

        // interceptor class must be injected to the same classloader as the target class that is intercepted
        new ByteBuddy().redefine(CountDirectoryScans.class)
                .make()
                .load(targetClassLoader,
                        ClassReloadingStrategy.fromInstalledAgent());

        new ByteBuddy().redefine(DirectoryFileTree.class)
                .visit(new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(ClassWriter.COMPUTE_FRAMES).method(ElementMatchers.named("visitFrom"), Advice.to(CountDirectoryScans.class)))
                .make()
                .load(targetClassLoader,
                        ClassReloadingStrategy.fromInstalledAgent());
    }
}
