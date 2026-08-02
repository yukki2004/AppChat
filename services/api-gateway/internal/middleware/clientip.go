// Package middleware — CookieAuthMiddleware, ClientIP.
package middleware

import (
	"strings"

	"github.com/gofiber/fiber/v2"
)

// ClientIP resolves the real client IP and sets it as X-Client-IP before forwarding
// downstream — the only source Core Service trusts for user_sessions.ip_address / geo-location
// (see docs/.../05-cookie-auth-flow.md E.10). Always overwrites any client-sent X-Client-IP;
// never forwards it verbatim, to prevent IP spoofing to dodge rate limits or fake a login
// location.
func ClientIP() fiber.Handler {
	return func(c *fiber.Ctx) error {
		realIP := c.Get("CF-Connecting-IP")
		if realIP == "" {
			if xff := c.Get("X-Forwarded-For"); xff != "" {
				realIP = strings.TrimSpace(strings.Split(xff, ",")[0])
			}
		}
		if realIP == "" {
			realIP = c.IP()
		}

		c.Request().Header.Set("X-Client-IP", realIP)
		return c.Next()
	}
}
