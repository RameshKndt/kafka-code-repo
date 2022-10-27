package com.example.producer;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.utils.Utils;

import java.util.List;
import java.util.Map;

public class MessagePartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();
        if ((keyBytes == null) || (!(key instanceof String)))
            throw new InvalidRecordException("We expect all messages " + "to have customer name as key");
        if (((String) key).equals("Banana"))
            return numPartitions - 1; // Banana will always go to last partition
        // Other records will get hashed to the rest of the partitions
        return Math.abs(Utils.murmur2(keyBytes)) % (numPartitions - 1);
    }

    @Override
    public void close() {
    }

    @Override
    public void onNewBatch(String topic, Cluster cluster, int prevPartition) {
        Partitioner.super.onNewBatch(topic, cluster, prevPartition);
    }

    @Override
    public void configure(Map<String, ?> map) {

    }
}
