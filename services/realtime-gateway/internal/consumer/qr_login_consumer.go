// Package consumer holds 1 file/struct per RabbitMQ consumer this service has — each struct
// implements rabbit.Consumer (exchange/queue/routing key identity + Handle) and owns everything
// specific to its own event: payload shape, dedup, what to do with the resolved connection.
// internal/base/rabbit.Wire owns the shared declare/bind/consume-loop boilerplate — a consumer
// here never repeats that.
package consumer

import (
	"context"
	"encoding/json"
	"log"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/redis/go-redis/v9"

	"chatapp/realtime-gateway/internal/base/message/qrlogin"
	"chatapp/realtime-gateway/internal/base/socket"
)

const (
	qrLoginUserExchange       = "user.exchange"
	qrLoginApprovedRoutingKey = "user.qr_login_approved"
	qrLoginApprovedQueue      = "realtime-gateway.user.qr_login_approved"
	qrLoginDedupTTL           = 6 * time.Hour
)


type QrLoginConsumer struct {
	registry *socket.Registry
	redis    *redis.Client
}

func NewQrLoginConsumer(registry *socket.Registry, redisClient *redis.Client) *QrLoginConsumer {
	return &QrLoginConsumer{registry: registry, redis: redisClient}
}

func (c *QrLoginConsumer) Exchange() string     { return qrLoginUserExchange }
func (c *QrLoginConsumer) ExchangeType() string { return "topic" }
func (c *QrLoginConsumer) Queue() string        { return qrLoginApprovedQueue }
func (c *QrLoginConsumer) RoutingKey() string   { return qrLoginApprovedRoutingKey }


type qrLoginApprovedPayload struct {
	QrToken string `json:"qr_token"`
}

func (c *QrLoginConsumer) Handle(ctx context.Context, d amqp.Delivery) {
	defer d.Ack(false)

	if d.MessageId == "" {
		log.Printf("QrLoginConsumer: delivery missing message_id (event_id), processing anyway")
	} else if !c.claimEventID(ctx, d.MessageId) {
		log.Printf("QrLoginConsumer: skipping duplicate event_id=%s", d.MessageId)
		return
	}

	var payload qrLoginApprovedPayload
	if err := json.Unmarshal(d.Body, &payload); err != nil {
		log.Printf("QrLoginConsumer: bad payload: %v", err)
		return
	}

	conn, found, err := c.registry.Resolve(ctx, qrlogin.RedisKey(payload.QrToken))
	if err != nil {
		log.Printf("QrLoginConsumer: registry lookup failed qrToken=%s: %v", payload.QrToken, err)
		return
	}
	if !found {
		log.Printf("QrLoginConsumer: no local connection for qrToken=%s", payload.QrToken)
		return
	}

	if err := socket.WriteJSON(conn, qrlogin.Approved); err != nil {
		log.Printf("QrLoginConsumer: push failed qrToken=%s: %v", payload.QrToken, err)
		return
	}
	conn.Close()
}


func (c *QrLoginConsumer) claimEventID(ctx context.Context, eventID string) bool {
	ok, err := c.redis.SetNX(ctx, "cache:received_event_dedup:"+eventID, "1", qrLoginDedupTTL).Result()
	if err != nil {
		log.Printf("QrLoginConsumer: dedup check failed event_id=%s: %v — processing anyway", eventID, err)
		return true
	}
	return ok
}
