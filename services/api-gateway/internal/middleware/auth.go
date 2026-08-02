package middleware

import (
	"context"
	"errors"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/lestrrat-go/jwx/v3/jwk"
	"github.com/lestrrat-go/jwx/v3/jwt"
	"github.com/redis/go-redis/v9"
)


func CookieAuth(jwksCache *jwk.Cache, jwksURL string, redisClient *redis.Client) fiber.Handler {
	return func(c *fiber.Ctx) error {
		token := c.Cookies("access_token")
		if token == "" {
			return unauthorized(c, "TOKEN_EXPIRED", "Missing access_token")
		}

		keySet, err := jwksCache.Lookup(c.Context(), jwksURL)
		if err != nil {
			return fiber.NewError(fiber.StatusBadGateway, "failed to load JWKS from core-service")
		}

		parsed, err := jwt.Parse([]byte(token), jwt.WithKeySet(keySet), jwt.WithValidate(true))
		if err != nil {
			if errors.Is(err, jwt.TokenExpiredError()) {
				return unauthorized(c, "TOKEN_EXPIRED", "access_token has expired")
			}
			return unauthorized(c, "TOKEN_EXPIRED", "Invalid access_token")
		}

		sub, ok := parsed.Subject()
		if !ok || sub == "" {
			return unauthorized(c, "TOKEN_EXPIRED", "access_token missing sub claim")
		}

		if revoked, err := isRevoked(c.Context(), redisClient, parsed, sub); err != nil {
			return fiber.NewError(fiber.StatusBadGateway, "failed to check revoke status")
		} else if revoked {
			return unauthorized(c, "TOKEN_REVOKED", "access_token has been revoked")
		}

		// Never trust a client-supplied X-User-Id — always overwrite with the verified sub.
		c.Request().Header.Set("X-User-Id", sub)
		return c.Next()
	}
}

func isRevoked(ctx context.Context, redisClient *redis.Client, token jwt.Token, sub string) (bool, error) {
	jti, hasJti := token.JwtID()
	if hasJti && jti != "" {
		exists, err := redisClient.Exists(ctx, "cache:jwt_blacklist:"+jti).Result()
		if err != nil {
			return false, err
		}
		if exists > 0 {
			return true, nil
		}
	}

	revokedBeforeRaw, err := redisClient.Get(ctx, "cache:jwt_revoked_before:"+sub).Result()
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return false, nil
		}
		return false, err
	}

	revokedBefore, err := time.Parse(time.RFC3339, revokedBeforeRaw)
	if err != nil {
		// Malformed value shouldn't lock everyone out — treat as "not revoked" and let it
		// be investigated separately, rather than turning a data bug into a full outage.
		return false, nil
	}

	iat, hasIat := token.IssuedAt()
	if !hasIat {
		return true, nil
	}
	return !iat.After(revokedBefore), nil
}

func unauthorized(c *fiber.Ctx, errorCode, message string) error {
	return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
		"success": false,
		"data":    nil,
		"error":   fiber.Map{"code": errorCode, "message": message},
	})
}
