package datadog.exceptions.instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Provides instrumentation of {@linkplain Throwable} constructor. <br> */
@AutoService(Instrumenter.class)
public final class ThrowableInstrumentation extends Instrumenter.Profiling {
  private final boolean hasJfr;

  public ThrowableInstrumentation() {
    super("throwables");
    /* Check only for the open-sources JFR implementation.
     * If it is ever needed to support also the closed sourced JDK 8 version the check should be
     * enhanced.
     * Need this custom check because ClassLoaderMatcher.hasClassesNamed() does not support bootstrap class loader yet.
     * Note: the downside of this is that we load some JFR classes at startup.
     * Note2: we cannot check that we can load ExceptionSampleEvent because it is not available on the class path yet.
     */
    hasJfr = ClassLoader.getSystemClassLoader().getResource("jdk/jfr/Event.class") != null;
  }

  @Override
  public String[] helperClassNames() {
    /*
     * Since the only instrumentation target is java.lang.Exception which is loaded by bootstrap classloader
     * it is ok to use helper classes instead of hacking around a Java 8 specific bootstrap.
     */
    return hasJfr
        ? new String[] {
          "com.datadog.profiling.exceptions.StreamingSampler",
          "com.datadog.profiling.exceptions.StreamingSampler$Counts",
          "com.datadog.profiling.exceptions.StreamingSampler$RollWindowTask",
          "com.datadog.profiling.exceptions.ExceptionCountEvent",
          "com.datadog.profiling.exceptions.ExceptionHistogram",
          "com.datadog.profiling.exceptions.ExceptionHistogram$Pair",
          "com.datadog.profiling.exceptions.ExceptionProfiling",
          "com.datadog.profiling.exceptions.ExceptionSampleEvent",
          "com.datadog.profiling.exceptions.ExceptionSampler",
          "datadog.exceptions.instrumentation.ThrowableInstanceAdviceHelper"
        }
        : new String[0];
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    if (hasJfr) {
      return is(Throwable.class);
    }
    return none();
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    if (hasJfr) {
      return Collections.singletonMap(isConstructor(), packageName + ".ThrowableInstanceAdvice");
    }
    return Collections.emptyMap();
  }
}
