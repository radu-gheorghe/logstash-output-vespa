package org.logstashplugins;

import ai.vespa.feed.client.*;
import ai.vespa.feed.client.impl.GracePeriodCircuitBreaker;
import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.LogstashPlugin;
import co.elastic.logstash.api.Output;
import co.elastic.logstash.api.PluginConfigSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.logstash.ObjectMappers;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

// class name must match plugin name
@LogstashPlugin(name = "vespa_feed")
public class VespaFeed implements Output {
    private static final Logger logger = LogManager.getLogger(VespaFeed.class);

    public static final PluginConfigSpec<URI> VESPA_URL =
            PluginConfigSpec.uriSetting("vespa_url", "http://localhost:8080");
    public static final PluginConfigSpec<String> NAMESPACE =
            PluginConfigSpec.requiredStringSetting("namespace");
    public static final PluginConfigSpec<String> DOCUMENT_TYPE =
            PluginConfigSpec.requiredStringSetting("document_type");
    public static final PluginConfigSpec<String> ID_FIELD =
            PluginConfigSpec.stringSetting("id_field", "id");

    // max HTTP/2 connections per endpoint. We only have 1
    public static final PluginConfigSpec<Long> MAX_CONNECTIONS =
            PluginConfigSpec.numSetting("max_connections", 1);
    // max streams for the async client. General wisdom is to prefer more streams than connections
    public static final PluginConfigSpec<Long> MAX_STREAMS =
            PluginConfigSpec.numSetting("max_streams", 128);
    // request timeout (seconds) for each write operation
    public static final PluginConfigSpec<Long> OPERATION_TIMEOUT =
            PluginConfigSpec.numSetting("operation_timeout", 180);
    // max retries for transient failures
    public static final PluginConfigSpec<Long> MAX_RETRIES =
            PluginConfigSpec.numSetting("max_retries", 10);
    // after this time (seconds), the circuit breaker will be half-open:
    // it will ping the endpoint to see if it's back,
    // then resume sending requests when it's back
    public static final PluginConfigSpec<Long> GRACE_PERIOD =
            PluginConfigSpec.numSetting("grace_period", 10);
    // this should close the client, but it looks like it doesn't shut down either Logstash or the plugin
    // when the connection can work again, Logstash seems to resume sending requests
    public static final PluginConfigSpec<Long> DOOM_PERIOD =
            PluginConfigSpec.numSetting("doom_period", 60);

    private final FeedClient client;
    private final String id;
    private final String namespace;
    private final String document_type;
    private final String id_field;
    private final OperationParameters operationParameters;
    private volatile boolean stopped = false;
    ObjectMapper objectMapper;


    public VespaFeed(final String id, final Configuration config, final Context context) {
        this.id = id;

        namespace = config.get(NAMESPACE);
        document_type = config.get(DOCUMENT_TYPE);
        id_field = config.get(ID_FIELD);
        operationParameters = OperationParameters.empty().timeout(Duration.ofSeconds(config.get(OPERATION_TIMEOUT)));

        client = FeedClientBuilder.create(config.get(VESPA_URL))
                    .setConnectionsPerEndpoint(config.get(MAX_CONNECTIONS).intValue())
                    .setMaxStreamPerConnection(config.get(MAX_STREAMS).intValue())
                    .setRetryStrategy(
                            new FeedClient.RetryStrategy() {
                                @Override
                                public boolean retry(FeedClient.OperationType type) {
                                    // retry all operations
                                    return true;
                                }

                                @Override
                                public int retries() {
                                    return config.get(MAX_RETRIES).intValue();
                                }
                            }
                    )
                    .setCircuitBreaker(
                            new GracePeriodCircuitBreaker(
                                    Duration.ofSeconds(config.get(GRACE_PERIOD)),
                                    Duration.ofSeconds(config.get(DOOM_PERIOD))
                            )
                    )
                .build();

        // for JSON serialization
        objectMapper = ObjectMappers.JSON_MAPPER;
    }

    @Override
    public void output(final Collection<Event> events) {
        // we put (async) indexing requests here
        List<CompletableFuture<Result>> promises = new ArrayList<>();

        Iterator<Event> z = events.iterator();
        while (z.hasNext() && !stopped) {
            Map<String, Object> eventData = z.next().getData();

            try {
                promises.add(asyncFeed(eventData));
            } catch (JsonProcessingException e) {
                logger.error("Error serializing event data into JSON: ", e);
            }
        }

        // wait for all futures to complete
        try {
            FeedClient.await(promises);
        } catch (MultiFeedException e) {
            e.feedExceptions().forEach(
                    exception -> logger.error("Error while waiting for async operation to complete: ",
                            exception)
            );
        }

    }

    private CompletableFuture<Result> asyncFeed(Map<String, Object> eventData) throws JsonProcessingException {
        // we put doc IDs here
        String docIdStr;

        // see if the event has an ID field (as configured)
        // if it does, use it as docIdStr. Otherwise, generate a UUID
        if (eventData.containsKey(id_field)) {
            docIdStr = eventData.get(id_field).toString();
        } else {
            docIdStr = UUID.randomUUID().toString();
        }
        DocumentId docId = DocumentId.of(namespace,
                document_type, docIdStr);

        // create a document from the event data. We need an enclosing "fields" object
        // to match the Vespa put format
        Map<String,Object> doc = new HashMap<>();
        doc.put("fields", eventData);

        // create the request to feed the document
        return client.put(docId, toJson(doc), operationParameters);
    }

    private String toJson(Map<String, Object> eventData) throws JsonProcessingException {
        return objectMapper.writeValueAsString(eventData);
    }

    @Override
    public void stop() {
        stopped = true;
        client.close();
    }

    @Override
    public void awaitStop() throws InterruptedException {
        // nothing to do here
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        return List.of(VESPA_URL, NAMESPACE, DOCUMENT_TYPE, ID_FIELD,
                MAX_CONNECTIONS, MAX_STREAMS, MAX_RETRIES, OPERATION_TIMEOUT);
    }

    @Override
    public String getId() {
        return id;
    }
}