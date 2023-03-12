package com.huabin.rabbit;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * @Author huabin
 * @DateTime 2023-03-12 10:32
 * @Desc
 */
public class RabbitTest {

    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
        connectionFactory.setHost("localhost");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");

        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        // 先进行一个exchange的创建，第三个true就是exchange的持久化设置
        channel.exchangeDeclare("myExchange", "direct", true);
        // 创建一个queue，第二个参数true就是queue的持久化设置
        channel.queueDeclare("myQueue", true, false, false, null);
        // 绑定exchange和queue
        channel.queueBind("myQueue", "myExchange", "myRKey");

        // 设置exchange到queue的消息无法路由的listener
        channel.addReturnListener(new ReturnListener() {
            @Override
            public void handleReturn(int i, String s, String s1, String s2, AMQP.BasicProperties basicProperties, byte[] bytes) throws IOException {
                System.out.println("ReturnListener - 从exchange路由到queue失败。消息=" + new String(bytes));
            }
        });

        // 接受ack消息
        channel.confirmSelect();
        channel.addConfirmListener(new ConfirmListener() {
            @Override
            public void handleAck(long l, boolean b) throws IOException {
                System.out.println("tagId=" + l);
            }

            @Override
            public void handleNack(long l, boolean b) throws IOException {
                System.out.println("tagId=" + l);
            }
        });

        // 发送消息
        byte[] msg = "Hello World!".getBytes();
        channel.basicPublish("myExchange", "myRKey",
                true,  // mandatory设置为true时才会走returnCallBack
                new AMQP.BasicProperties().builder()
                        .deliveryMode(2)  // 消息持久化设置
                        .contentType("text/plain")
                        .build(),
                msg);

        // 消息消费
        channel.basicConsume("myQueue", false, "myConsumer", new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                System.out.println("接收到了消息 = "+new String(body));
                channel.basicAck(envelope.getDeliveryTag(), true);
                System.out.println("deliveryTag=" + envelope.getDeliveryTag());
            }
        });

    }

}
