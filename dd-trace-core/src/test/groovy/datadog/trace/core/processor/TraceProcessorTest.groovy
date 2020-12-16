package datadog.trace.core.processor

import datadog.trace.bootstrap.instrumentation.api.InstrumentationTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.common.writer.ListWriter
import datadog.trace.core.CoreTracer
import datadog.trace.core.processor.rule.URLAsResourceNameRule
import datadog.trace.test.util.DDSpecification
import spock.lang.Subject

class TraceProcessorTest extends DDSpecification {

  @Subject
  def processor = new TraceProcessor()
  def tracer = CoreTracer.builder().writer(new ListWriter()).build()
  def span = tracer.buildSpan("fakeOperation").start()
  def trace = [span]

  def cleanup() {
    tracer?.close()
  }

  def "test disable"() {
    setup:
    injectSysConfig("trace.${name}.enabled", "false")
    def processor = new TraceProcessor()

    expect:
    !processor.rules.any {
      it.class.name == rule.name
    }

    where:
    rule                  | alias
    URLAsResourceNameRule | null
    URLAsResourceNameRule | URLAsResourceNameRule.simpleName.toLowerCase()
    URLAsResourceNameRule | "URLAsResourceName"
    URLAsResourceNameRule | "Status404Rule"
    URLAsResourceNameRule | "Status404Decorator"

    name = alias == null ? rule.simpleName : alias
  }

  def "set 404 as a resource on a 404 issue"() {
    setup:
    span.setTag(Tags.HTTP_STATUS, 404)

    when:
    processor.onTraceComplete(trace)

    then:
    span.getResourceName() == "404"
  }

  def "resource name set with url path #url to #resourceName"() {
    setup:
    if (method) {
      span.setTag(Tags.HTTP_METHOD, method)
    }
    span.setTag(Tags.HTTP_URL, url)
    span.setTag(Tags.HTTP_STATUS, status)

    when:
    processor.onTraceComplete(trace)

    then:
    span.resourceName.toString() == resourceName

    where:
    method | url      | status | resourceName
    "GET"  | ""       | 200    | "fakeOperation"
    null   | "/"      | 200    | "/"
    null   | "/path"  | 200    | "/path"
    "put"  | "/"      | 200    | "PUT /"
    "Head" | "/path"  | 200    | "HEAD /path"
    "post" | "/post"  | 400    | "POST /post"
    "GET"  | "/asdf"  | 404    | "404"
    null   | "/error" | 500    | "/error"
  }

  def "convert _dd.measured to metric"() {
    when:
    span.setMeasured(true)

    then:
    span.isMeasured()

    when:
    processor.onTraceComplete(trace)

    then:
    span.isMeasured()
    span.unsafeMetrics.get(InstrumentationTags.DD_MEASURED) == null
    span.tags.get(InstrumentationTags.DD_MEASURED) == null
  }
}
