package dmo.fs.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dmo.fs.utils.ColorUtilConstants;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;

@IfBuildProperty(name = "DODEX_KAFKA", stringValue = "true")
@Unremovable
@Priority(10)
@ApplicationScoped
public class KafkaEmitterDodex {
    private static final Logger logger = LoggerFactory.getLogger(KafkaEmitterDodex.class.getName());
    private static final String channel = "dodex-events-out";
    private static int toggle;
    private Integer dodexEventsPartitions = 2;
    private String dodexEventsTopic = "dodex-events";
    private static Boolean removeMessages = false;
    private static Integer messageLimit = 25;

    @Inject
    @Channel(channel)
    Emitter<Integer> countEmitter;

//    {
//        this.setConfig();
//    }

    public KafkaEmitterDodex() {
        setConfig();
    }

    public void setValue(Integer value) {
        setValue("broadcast", value);
    }

    public void setValue(String key, Integer value) {
        if (value == null) {
            value = 0;
        }
        int partition = toggle++ % dodexEventsPartitions;
        if (toggle == dodexEventsPartitions) {
            toggle = 0;
        }
        int offset = TimeZone.getDefault().getOffset(Instant.now().toEpochMilli());
        if (logger.isDebugEnabled()) {
            logger.info("Emitting data: {}--{}--{}--{}--{}", offset, key, partition, dodexEventsTopic, value);
        }
        Message<Integer> message = Message.of(value)
          .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
            .withKey(key)
            .withPartition(partition)
            .withTimestamp(Instant.ofEpochMilli(System.currentTimeMillis() - (-offset)))
            .withTopic(dodexEventsTopic)
            .build());

        countEmitter.send(message
          .withAck(() -> CompletableFuture.completedFuture(null))
          .withNack(throwable -> {
              logger.info("Not Acknowledged: {}", throwable.getMessage());
              return CompletableFuture.completedFuture(null);
          }));
    }

    private void setConfig() {
        JsonObject jsonObject;
        ObjectMapper jsonMapper = new ObjectMapper();
        JsonNode node;
        try {
            try (InputStream in = getClass().getResourceAsStream("/application-conf.json")) {
                node = jsonMapper.readTree(in);
            }
            jsonObject = JsonObject.mapFrom(node);

            final Optional<Integer> optionalEventsPartitions = Optional.ofNullable(jsonObject.getInteger("dodex.events.partitions"));
            optionalEventsPartitions.ifPresent(integer -> dodexEventsPartitions = integer);
            final Optional<String> optionalEventsTopic = Optional.ofNullable(jsonObject.getString("dodex.events.topic"));
            optionalEventsTopic.ifPresent(s -> dodexEventsTopic = s);
            final Optional<Integer> optionalMessageLimit = Optional.ofNullable(jsonObject.getInteger("dodex.events.limit"));
            optionalMessageLimit.ifPresent(integer -> messageLimit = integer);
            final Optional<Boolean> optionalRemoveMessages = Optional.ofNullable(jsonObject.getBoolean("dodex.events.remove"));
            optionalRemoveMessages.ifPresent(aBoolean -> removeMessages = aBoolean);
        } catch (final Exception exception) {
            logger.info(String.format("%sContext Configuration failed...%s%s", ColorUtilConstants.RED_BOLD_BRIGHT,
              exception.getMessage(), ColorUtilConstants.RESET));
            exception.printStackTrace();
        }
    }

    public static Integer getMessageLimit() {
        return messageLimit;
    }

    public static Boolean getRemoveMessages() {
        return removeMessages;
    }
}
