// Package proxy forwards requests to the appropriate downstream service. No business logic —
// routing/auth/rate-limit is the Gateway's whole job, per services/api-gateway/CLAUDE.md.
package proxy

import (
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/proxy"
)


func Forward(baseURL string) fiber.Handler {
	return func(c *fiber.Ctx) error {
		target := baseURL + c.OriginalURL()

		allowOrigin := string(c.Response().Header.Peek(fiber.HeaderAccessControlAllowOrigin))
		allowCredentials := string(c.Response().Header.Peek(fiber.HeaderAccessControlAllowCredentials))
		vary := string(c.Response().Header.Peek(fiber.HeaderVary))

		if err := proxy.Do(c, target); err != nil {
			return fiber.NewError(fiber.StatusBadGateway, "upstream request failed")
		}

		c.Response().Header.Del(fiber.HeaderConnection)

		if allowOrigin != "" {
			c.Response().Header.Set(fiber.HeaderAccessControlAllowOrigin, allowOrigin)
		}
		if allowCredentials != "" {
			c.Response().Header.Set(fiber.HeaderAccessControlAllowCredentials, allowCredentials)
		}
		if vary != "" {
			c.Response().Header.Set(fiber.HeaderVary, vary)
		}
		return nil
	}
}
