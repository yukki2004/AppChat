package socket

import (
	"time"

	"github.com/gofiber/contrib/websocket"
)

const (
	pingInterval = 20 * time.Second 
	pongWait     = 60 * time.Second 
)


func StartHeartbeat(c *websocket.Conn) (stop func()) {
	c.SetReadDeadline(time.Now().Add(pongWait))
	c.SetPongHandler(func(string) error {
		c.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	stopCh := make(chan struct{})
	go func() {
		ticker := time.NewTicker(pingInterval)
		defer ticker.Stop()
		for {
			select {
			case <-stopCh:
				return
			case <-ticker.C:
				if err := c.WriteMessage(websocket.PingMessage, nil); err != nil {
					return
				}
			}
		}
	}()
	return func() { close(stopCh) }
}


func BlockUntilClosed(c *websocket.Conn) {
	for {
		if _, _, err := c.ReadMessage(); err != nil {
			return
		}
	}
}
