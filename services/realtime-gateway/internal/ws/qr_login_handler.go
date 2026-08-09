package ws

import (
	"context"
	"log"
	"time"

	"github.com/gofiber/contrib/websocket"
	"github.com/google/uuid"

	"chatapp/realtime-gateway/internal/base/message/qrlogin"
	"chatapp/realtime-gateway/internal/base/socket"
)


func QrLoginHandler(registry *socket.Registry) func(*websocket.Conn) {
	return func(c *websocket.Conn) {
		qrToken := c.Query("token")
		if qrToken == "" {
			log.Printf("qr-login ws: rejected, missing token query param")
			c.Close()
			return
		}
		connID := uuid.NewString()
		ctx := context.Background()
		redisKey := qrlogin.RedisKey(qrToken)

		if err := registry.Register(ctx, redisKey, connID, c, qrlogin.TTL); err != nil {
			log.Printf("qr-login ws: register failed qrToken=%s: %v", qrToken, err)
			c.Close()
			return
		}
		log.Printf("qr-login ws: connected qrToken=%s connID=%s", qrToken, connID)
		defer func() {
			registry.Unregister(ctx, redisKey, connID)
			log.Printf("qr-login ws: disconnected qrToken=%s connID=%s", qrToken, connID)
		}()

		stopHeartbeat := socket.StartHeartbeat(c)
		defer stopHeartbeat()

		expireTimer := time.AfterFunc(qrlogin.TTL, func() {
			socket.WriteJSON(c, qrlogin.Expired)
			c.Close()
		})
		defer expireTimer.Stop()

		socket.BlockUntilClosed(c)
	}
}
