
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
    public static void install() throws ClassNotFoundException {
        ByteBuddyAgent.install();

        ClassLoader targetClassLoader = DirectoryFileTree.class.getClassLoader();

        ByteBuddy byteBuddy = new ByteBuddy();

        // interceptor class must be injected to the same classloader as the target class that is intercepted
        for (Class<?> clazz : new Class<?>[]{CountDirectoryScans.class, CountCacheStats.class, CountCacheStats.Hits.class, CountCacheStats.Misses.class}) {
            byteBuddy.redefine(clazz)
                    .make()
                    .load(targetClassLoader,
                            ClassReloadingStrategy.fromInstalledAgent());
        }

        byteBuddy.redefine(DirectoryFileTree.class)
                .visit(new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(ClassWriter.COMPUTE_FRAMES).method(ElementMatchers.named("visitFrom"), Advice.to(CountDirectoryScans.class)))
                .make()
                .load(targetClassLoader,
                        ClassReloadingStrategy.fromInstalledAgent());


        byteBuddy.redefine(targetClassLoader.loadClass("org.gradle.api.internal.changedetection.state.CachingTreeVisitor"))
                .visit(new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(ClassWriter.COMPUTE_FRAMES)
                        .method(ElementMatchers.named("recordCacheHit"), Advice.to(CountCacheStats.Hits.class))
                        .method(ElementMatchers.named("recordCacheMiss"), Advice.to(CountCacheStats.Misses.class))
                )
                .make()
                .load(targetClassLoader,
                        ClassReloadingStrategy.fromInstalledAgent());
    }
}
