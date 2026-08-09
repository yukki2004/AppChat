package socket

import (
	"encoding/json"

	"github.com/gofiber/contrib/websocket"
)


func WriteJSON(c *websocket.Conn, v any) error {
	body, err := json.Marshal(v)
	if err != nil {
		return err
	}
	return c.WriteMessage(websocket.TextMessage, body)
}
