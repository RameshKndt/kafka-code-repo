package com.example.joins.solution;

import com.example.StreamsUtils;
import com.example.avro.User;
import com.example.avro.ApplianceOrder;
import com.example.avro.CombinedOrder;
import com.example.avro.ElectronicOrder;
import com.example.joins.TopicLoader;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

public class StreamsJoin {

    static <T extends SpecificRecord> SpecificAvroSerde<T> getSpecificAvroSerde(final Map<String, Object> serdeConfig) {
        final SpecificAvroSerde<T> specificAvroSerde = new SpecificAvroSerde<>();
        specificAvroSerde.configure(serdeConfig, false);
        return specificAvroSerde;
    }

    public static void main(String[] args) throws IOException {
        Properties streamsProps = StreamsUtils.loadProperties();
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "joining-streams");

        StreamsBuilder builder = new StreamsBuilder();
        String streamOneInput = streamsProps.getProperty("stream_one.input.topic");
        String streamTwoInput = streamsProps.getProperty("stream_two.input.topic");
        String tableInput = streamsProps.getProperty("table.input.topic");
        String outputTopic = streamsProps.getProperty("joins.output.topic");

        Map<String, Object> configMap = StreamsUtils.propertiesToMap(streamsProps);

        SpecificAvroSerde<ApplianceOrder> applianceSerde = getSpecificAvroSerde(configMap);
        SpecificAvroSerde<ElectronicOrder> electronicSerde = getSpecificAvroSerde(configMap);
        SpecificAvroSerde<CombinedOrder> combinedSerde = getSpecificAvroSerde(configMap);
        SpecificAvroSerde<User> userSerde = getSpecificAvroSerde(configMap);

        ValueJoiner<ApplianceOrder, ElectronicOrder, CombinedOrder> orderJoiner =
                (applianceOrder, electronicOrder) -> CombinedOrder.newBuilder()
                        .setApplianceOrderId(applianceOrder.getOrderId())
                        .setApplianceId(applianceOrder.getApplianceId())
                        .setElectronicOrderId(electronicOrder.getOrderId())
                        .setTime(Instant.now().toEpochMilli())
                        .build();

        ValueJoiner<CombinedOrder, User, CombinedOrder> enrichmentJoiner = (combined, user) -> {
            if (user != null) {
                combined.setUserName(user.getName());
            }
            return combined;
        };

        KStream<String, ApplianceOrder> applianceStream =
                builder.stream(streamOneInput, Consumed.with(Serdes.String(), applianceSerde))
                        .peek((key, value) -> System.out.println("Appliance stream incoming record key " + key + " value " + value));

        KStream<String, ElectronicOrder> electronicStream =
                builder.stream(streamTwoInput, Consumed.with(Serdes.String(), electronicSerde))
                        .peek((key, value) -> System.out.println("Electronic stream incoming record " + key + " value " + value));

        KTable<String, User> userTable =
                builder.table(tableInput, Materialized.with(Serdes.String(), userSerde));


        KStream<String, CombinedOrder> combinedStream =
                applianceStream.join(
                        electronicStream,
                        orderJoiner,
                        JoinWindows.of(Duration.ofMinutes(30)),
                        StreamJoined.with(Serdes.String(), applianceSerde, electronicSerde))
                .peek((key, value) -> System.out.println("Stream-Stream Join record key " + key + " value " + value));

        combinedStream.leftJoin(
                userTable,
                enrichmentJoiner,
                Joined.with(Serdes.String(), combinedSerde, userSerde))
                .peek((key, value) -> System.out.println("Stream-Table Join record key " + key + " value " + value))
                .to(outputTopic, Produced.with(Serdes.String(), combinedSerde));

        KafkaStreams kafkaStreams = new KafkaStreams(builder.build(), streamsProps);
        TopicLoader.runProducer();
        kafkaStreams.start();

    }
}
