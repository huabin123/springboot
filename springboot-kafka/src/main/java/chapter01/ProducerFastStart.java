package chapter01;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * @Author huabin
 * @DateTime 2023-03-15 15:26
 * @Desc
 */
public class ProducerFastStart {

    public static final String brokerList = "localhost:9092";
    public static final String topic = "test";

    public static void main(String[] args) {

        Properties properties = new Properties();
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("bootstrap.servers", brokerList);

        //配置生产者客户端参数并创建KafkaProducer 实例
        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);
        // 生产消息
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "hello, Kafka!");

        try {
            producer.send(record);
//            producer.send(record).get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        producer.close();

    }

}
