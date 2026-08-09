package rabbit

import (
	"context"
	"fmt"
	"log"

	amqp "github.com/rabbitmq/amqp091-go"
)


type Consumer interface {
	Exchange() string
	ExchangeType() string
	Queue() string
	RoutingKey() string
	Handle(ctx context.Context, d amqp.Delivery)
}

func Wire(ctx context.Context, conn *amqp.Connection, consumers ...Consumer) error {
	for _, c := range consumers {
		if err := wireOne(ctx, conn, c); err != nil {
			return fmt.Errorf("wire consumer queue=%s: %w", c.Queue(), err)
		}
	}
	return nil
}

func wireOne(ctx context.Context, conn *amqp.Connection, c Consumer) error {
	ch, err := conn.Channel()
	if err != nil {
		return err
	}

	deliveries, err := DeclareAndConsume(ch, c.Exchange(), c.ExchangeType(), c.Queue(), c.RoutingKey())
	if err != nil {
		return err
	}

	go func() {
		for {
			select {
			case <-ctx.Done():
				ch.Close()
				return
			case d, ok := <-deliveries:
				if !ok {
					return
				}
				c.Handle(ctx, d)
			}
		}
	}()

	log.Printf("rabbit: consuming queue=%s (bound to %s/%s)", c.Queue(), c.Exchange(), c.RoutingKey())
	return nil
}
