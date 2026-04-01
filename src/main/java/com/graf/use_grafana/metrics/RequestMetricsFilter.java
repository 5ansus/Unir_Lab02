package com.graf.use_grafana.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestMetricsFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(RequestMetricsFilter.class);

  private final Counter requestCounter;
  private final Counter logLineCounter;
  private final Timer requestLatency;

  public RequestMetricsFilter(MeterRegistry registry) {
    this.requestCounter = Counter.builder("app_requests_total")
        .description("Total number of HTTP requests processed by the application")
        .register(registry);

    this.logLineCounter = Counter.builder("app_log_lines_total")
        .description("Total number of application log lines emitted")
        .register(registry);

    this.requestLatency = Timer.builder("app_request_latency_seconds")
        .description("HTTP request latency")
        .publishPercentileHistogram(true)
        .register(registry);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    long startNs = System.nanoTime();
    requestCounter.increment();

    logger.info("Incoming request: method={} path={}", request.getMethod(), request.getRequestURI());
    logLineCounter.increment();

    try {
      filterChain.doFilter(request, response);
    } finally {
      double elapsedSeconds = (System.nanoTime() - startNs) / 1_000_000_000.0;
      requestLatency.record((long) (elapsedSeconds * 1_000_000_000L), java.util.concurrent.TimeUnit.NANOSECONDS);

      logger.info(
          "Outgoing response: method={} path={} status={} latencyMs={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          String.format("%.2f", elapsedSeconds * 1000)
      );
      logLineCounter.increment();
    }
  }
}
