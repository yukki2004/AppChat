package main

import (
	"context"
	"log"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/requestid"
	"github.com/lestrrat-go/httprc/v3"
	"github.com/lestrrat-go/jwx/v3/jwk"
	"github.com/redis/go-redis/v9"

	"chatapp/api-gateway/internal/config"
	"chatapp/api-gateway/internal/health"
	"chatapp/api-gateway/internal/middleware"
	"chatapp/api-gateway/internal/proxy"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	redisClient := redis.NewClient(&redis.Options{
		Addr:     cfg.RedisHost + ":" + cfg.RedisPort,
		Password: cfg.RedisPassword,
	})

	jwksURL := cfg.CoreServiceURL + "/.well-known/jwks.json"
	jwksCache, err := jwk.NewCache(context.Background(), httprc.NewClient())
	if err != nil {
		log.Fatalf("create JWKS cache: %v", err)
	}
	if err := jwksCache.Register(context.Background(), jwksURL); err != nil {
		log.Fatalf("register JWKS URL %s: %v", jwksURL, err)
	}

	app := fiber.New(fiber.Config{DisableStartupMessage: true})

	app.Use(requestid.New())
	app.Use(cors.New(cors.Config{
		AllowOrigins:     "http://localhost:5500,http://127.0.0.1:5500",
		AllowCredentials: true,
		AllowHeaders: "Content-Type,X-Forwarded-For",
	}))
	app.Use(middleware.ClientIP())

	app.Get("/health", health.Handler(cfg.Env))
	app.Get("/health/live", health.Live)
	app.Get("/health/ready", health.Ready)

	forward := proxy.Forward(cfg.CoreServiceURL)
	authRequired := middleware.CookieAuth(jwksCache, jwksURL, redisClient)

	// Public — no access_token needed yet (these ARE the auth flow that produces one).
	app.Get("/.well-known/jwks.json", forward)
	app.Post("/auth/register", forward)
	app.Post("/auth/login", forward)
	app.Post("/auth/login/2fa/challenge", forward)
	app.Post("/auth/login/2fa", forward)
	// Also public: /auth/logout works off the refresh_token cookie alone (see E.7) and must
	// still succeed when access_token is already expired/missing — logging out shouldn't
	// require a currently-valid access token.
	app.Post("/auth/logout", forward)

	// Protected — requires a verified access_token, forwarded downstream as X-User-Id.
	app.Post("/2fa/totp/setup", authRequired, forward)
	app.Post("/2fa/totp/confirm", authRequired, forward)
	app.Post("/2fa/email/setup", authRequired, forward)
	app.Post("/2fa/email/confirm", authRequired, forward)
	app.Delete("/2fa/:method", authRequired, forward)
	app.Delete("/auth/sessions/:sessionId", authRequired, forward)
	app.Post("/auth/logout-all", authRequired, forward)

	log.Printf("api-gateway starting on :%s (env=%s, core_service=%s)", cfg.Port, cfg.Env, cfg.CoreServiceURL)
	if err := app.Listen(":" + cfg.Port); err != nil {
		log.Fatalf("server error: %v", err)
	}
}
