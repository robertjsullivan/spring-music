package org.cloudfoundry.samples.music.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author Sumit Deo (deosu@vmware.com)
 */

@Service
public class OTelConfig implements CommandLineRunner {
    private static final String SERVICE_NAME_VALUE = "otel-java-svc";
    public static final String OTEL_COLLECTOR_ENDPOINT = "https://wavefront-proxy.service.internal:4317";
    public static final String APP_NAME_VALUE = "spring-music-bob";
    public static final String SERVICE_NAME_KEY = "service.name";
    public static final String APP_NAME_KEY = "application";
    public static final String INSTRUMENTATION_LIBRARY_NAME = "instrumentation-library-name";
    public static final String INSTRUMENTATION_VERSION = "1.0.0";
    static Tracer tracer;


    // Adds a BatchSpanProcessor initialized with OtlpGrpcSpanExporter to the TracerSdkProvider.
    static OpenTelemetry initOpenTelemetry() {
        OtlpGrpcSpanExporter spanExporter = getOtlpGrpcSpanExporter();
        BatchSpanProcessor spanProcessor = getBatchSpanProcessor(spanExporter);
        SdkTracerProvider tracerProvider = getSdkTracerProvider(spanProcessor);
        OpenTelemetrySdk openTelemetrySdk = getOpenTelemetrySdk(tracerProvider);
        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::shutdown));

        return openTelemetrySdk;
    }

    public static Resource resource() {
        return Resource.getDefault().merge(Resource
                .create(Attributes.builder().put(SERVICE_NAME_KEY, SERVICE_NAME_VALUE).put(APP_NAME_KEY, APP_NAME_VALUE).build()));
    }

    private static OpenTelemetrySdk getOpenTelemetrySdk(SdkTracerProvider tracerProvider) {
        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }

    private static SdkTracerProvider getSdkTracerProvider(BatchSpanProcessor spanProcessor) {
        return SdkTracerProvider.builder().addSpanProcessor(spanProcessor)
                .setResource(resource()).build();
    }

    private static BatchSpanProcessor getBatchSpanProcessor(OtlpGrpcSpanExporter spanExporter) {
        return BatchSpanProcessor.builder(spanExporter)
                .setScheduleDelay(100, TimeUnit.MILLISECONDS).build();
    }

    private static OtlpGrpcSpanExporter getOtlpGrpcSpanExporter() {
        System.out.println("sending to enpoint: "+OTEL_COLLECTOR_ENDPOINT);
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(OTEL_COLLECTOR_ENDPOINT)
                .setTimeout(2, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void run(String... args) throws Exception {

    /*
        Tracer must be acquired, which is responsible for creating spans and interacting with the
          Context
         */
        tracer = getTracer();
        while (true) {
            // An automated way to propagate the parent span on the current thread
            for (int index = 0; index < 3; index++) {
            /*
            Create a span by specifying the name of the span. The start and end time of the span
            is automatically set by the OpenTelemetry SDK
             */
                Span parentSpan = tracer.spanBuilder("parentSpan" + index).setNoParent().startSpan();
                System.out.println("In parent method. TraceID : "+parentSpan.getSpanContext().getTraceId());

                try {
                    // Put the span into the current Context
                    parentSpan.makeCurrent();

                /*
                Annotate the span with attributes specific to the represented operation, to
                provide additional context
                 */
                    parentSpan.setAttribute("parentIndex", index);

                    // Sleep to simulate some work being done before calling `childMethod`
                    Thread.sleep(500);
                    childMethod(parentSpan, index);

                    // Sleep to simulate work being done after `childMethod` returns
                    Thread.sleep(500 * index);
                } catch (Throwable throwable) {
                    parentSpan.setStatus(StatusCode.ERROR, "Exception message: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return;
                } finally {
                    // Closing the scope does not end the span, this has to be done manually
                    parentSpan.end();
                }
            }

            // Sleep for a bit to let everything settle
            Thread.sleep(20000);
        }
    }

    private static void childMethod(Span parentSpan, int index) {
        tracer = getTracer();

        // `setParent(...)` is not required, `Span.current()` is automatically added as the parent
        Span childSpan = tracer.spanBuilder("childSpan").setParent(Context.current().with(parentSpan))
                .startSpan();
        System.out.println("In child method. TraceID : "+childSpan.getSpanContext().getTraceId());
        Attributes eventAttrs = Attributes.builder().put("a-key", "a-val").build();
        childSpan.addEvent("child-event", eventAttrs);

        // Put the span into the current Context
        try (Scope scope = childSpan.makeCurrent()) {
            if (index == 1) {
                childSpan.setStatus(StatusCode.ERROR, "Errored (arbitrarily) because index=1");
            }
            Thread.sleep(1000);
        } catch (Throwable throwable) {
            childSpan.setStatus(StatusCode.ERROR, "Something wrong with the child span");
            throwable.printStackTrace();
        } finally {
            childSpan.end();
        }
    }

    private static synchronized Tracer getTracer() {
        if (tracer == null) {

            /*
            It is important to initialize your SDK as early as possible in your application's
            lifecycle
             */
            OpenTelemetry openTelemetry = OTelConfig.initOpenTelemetry();

            // Get a tracer
            tracer = openTelemetry.getTracer(INSTRUMENTATION_LIBRARY_NAME, INSTRUMENTATION_VERSION);
        }

        return tracer;
    }
}
