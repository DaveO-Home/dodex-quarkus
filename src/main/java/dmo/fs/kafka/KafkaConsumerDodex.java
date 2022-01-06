package dmo.fs.kafka;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteRecordsResult;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;

@IfBuildProperty(name = "DODEX_KAFKA", stringValue = "true")
@Path("/events/{command}/{init}")
@RequestScoped
public class KafkaConsumerDodex {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerDodex.class.getName());
    private static final Queue<DodexEventData> eventQueue = new LinkedList<>();
    private static final String topic = "dodex-events";
    private static final Set<DodexEventData> dodexEventData = Collections.newSetFromMap(Collections.synchronizedMap(new LinkedHashMap<>()));
    private static final int dodexEventsLimit = KafkaEmitterDodex.getMessageLimit();

    @Incoming(topic)
    @NonBlocking
    @Acknowledgment(Acknowledgment.Strategy.NONE) //.POST_PROCESSING)
    @SuppressWarnings("unchecked")
    public CompletionStage<Void> consume(Message<Integer> message) {
        Iterator<Object> data = message.getMetadata().iterator();
        Integer payload = 0;

        while(data.hasNext()) {
            Object next = data.next();
            if(next instanceof IncomingKafkaRecordMetadata) {
                ConsumerRecord<String, String> record = (ConsumerRecord<String, String>)((IncomingKafkaRecordMetadata<String, String>) next).getRecord();
                String key = record.key(); 
                String topic = record.topic();
                Timestamp timestamp = new Timestamp(record.timestamp());
                int partition = record.partition();
                long offset = record.offset();

                try {
                    payload = message.getPayload();
                    if(dodexEventData.size() > dodexEventsLimit) {
                        dodexEventData.clear();
                    }
                    dodexEventData.add(new DodexEventData(key, topic, payload, timestamp, partition, offset));
                } catch (Exception ex) {
                    logger.info("Payload Error: {}", ex.getMessage());
                }
                
                if(logger.isDebugEnabled()) {
                    logger.info("Consumer Payload: {}--{}--{}--{}", key, topic, payload, KafkaEmitterDodex.getRemoveMessages());
                }
                if(KafkaEmitterDodex.getRemoveMessages()) {
                    removeMessages(topic, offset, partition);
                }
                break;
            }
        }
        
        return message.ack();
    }

    @GET
    public Set<DodexEventData> list(@PathParam String command, @PathParam Integer init) {
        // let a new monitor start with fresh cache
        if(init == 0) {
            dodexEventData.clear();
        }
        return dodexEventData;
    }

    /*
        Removing messges was just an exercise in leaning Kafka - This will delete half of the messages
        if a certain limit is reached (assuming the offset increments by one).
        The proper way to do this is with 'log.retention.bytes' and 'log.retention.(hours/minutes/ms)'
        in the server.properties. And, if needed, 'kafka-configs.sh --alter' at runtime.

    */
    private void removeMessages(String topic, long offset, int partition) {
        // per @amethystic Counting Number of messages stored in a kafka topic
        Properties props = new Properties();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class.getName());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dodex");

        Long totalCount = 0l;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> ll = new LinkedList<>();
            ll.add (new TopicPartition(topic, partition));
            consumer.assign(ll);
            Set<TopicPartition> assignment;

            while ((assignment = consumer.assignment()).isEmpty()) {
                consumer.poll(Duration.ofMillis(500));
            }
            final Map<TopicPartition, Long> endOffsets = consumer.endOffsets(assignment);
            final Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(assignment);
            assert endOffsets.size() == beginningOffsets.size();
            assert endOffsets.keySet().equals(beginningOffsets.keySet());

            totalCount = beginningOffsets.entrySet().stream().mapToLong(entry -> {
                    TopicPartition tp = entry.getKey();
                    Long beginningOffset = entry.getValue();
                    Long endOffset = endOffsets.get(tp);
                    return endOffset - beginningOffset;
                }).sum();
            
            beginningOffsets.clear();
            consumer.close(Duration.ofMillis(500));
        }
        
        if (totalCount > KafkaEmitterDodex.getMessageLimit()) {
            long toDelete = offset - totalCount/2;
            TopicPartition tp = new TopicPartition(topic, partition);
            RecordsToDelete rtd = RecordsToDelete.beforeOffset(toDelete);
            Map<TopicPartition, RecordsToDelete> deleteRecords = new ConcurrentHashMap<>();
            deleteRecords.put(tp, rtd);
            Properties config = new Properties();
            config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

            try {
                AdminClient ac = AdminClient.create(config);
                DeleteRecordsResult dr = ac.deleteRecords(deleteRecords);
                dr.all().get(1l, TimeUnit.SECONDS);
                logger.info("Approximate records deleted: {}", totalCount/2);
                ac.close();
            } catch (InterruptedException | TimeoutException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                logger.error("Delete Messages Exception: {}", e.getMessage());
            }
        }
    }
    public static Queue<DodexEventData> getEventqueue() {
        return eventQueue;
    }
}
