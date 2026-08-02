// Package health exposes health check endpoints for K8s probes.
package health

import "github.com/gofiber/fiber/v2"

func Handler(env string) fiber.Handler {
	return func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok", "env": env})
	}
}

func Live(c *fiber.Ctx) error {
	return c.SendStatus(fiber.StatusOK)
}

func Ready(c *fiber.Ctx) error {
	return c.SendStatus(fiber.StatusOK)
}
