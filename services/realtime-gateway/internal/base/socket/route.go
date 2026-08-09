package socket

import (
	"github.com/gofiber/contrib/websocket"
	"github.com/gofiber/fiber/v2"
)


func RegisterRoute(app *fiber.App, path string, handler func(*websocket.Conn)) {
	app.Use(path, func(c *fiber.Ctx) error {
		if websocket.IsWebSocketUpgrade(c) {
			return c.Next()
		}
		return fiber.ErrUpgradeRequired
	})
	app.Get(path, websocket.New(handler))
}
