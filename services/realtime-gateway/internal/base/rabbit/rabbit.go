package rabbit

import (
	amqp "github.com/rabbitmq/amqp091-go"
)


func DeclareAndConsume(ch *amqp.Channel, exchange, exchangeType, queue, routingKey string) (<-chan amqp.Delivery, error) {
	if err := ch.ExchangeDeclarePassive(exchange, exchangeType, true, false, false, false, nil); err != nil {
		return nil, err
	}
	if _, err := ch.QueueDeclare(queue, true, false, false, false, nil); err != nil {
		return nil, err
	}
	if err := ch.QueueBind(queue, routingKey, exchange, false, nil); err != nil {
		return nil, err
	}
	return ch.Consume(queue, "", false, false, false, false, nil)
}
