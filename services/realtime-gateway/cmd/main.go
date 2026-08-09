package main

import (
	"context"
	"log"
	"github.com/gofiber/fiber/v2"
	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/redis/go-redis/v9"
	"chatapp/realtime-gateway/internal/base/rabbit"
	"chatapp/realtime-gateway/internal/base/socket"
	"chatapp/realtime-gateway/internal/config"
	"chatapp/realtime-gateway/internal/consumer"
	"chatapp/realtime-gateway/internal/ws"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	redisClient := redis.NewClient(&redis.Options{Addr: cfg.RedisAddr})

	rabbitConn, err := amqp.Dial(cfg.RabbitMQURL)
	if err != nil {
		log.Fatalf("dial rabbitmq: %v", err)
	}
	defer rabbitConn.Close()

	registry := socket.NewRegistry(redisClient)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	if err := rabbit.Wire(ctx, rabbitConn,
		consumer.NewQrLoginConsumer(registry, redisClient),
	); err != nil {
		log.Fatalf("wire consumers: %v", err)
	}

	app := fiber.New()

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok", "env": cfg.Env})
	})

	socket.RegisterRoute(app, "/ws/qr-login", ws.QrLoginHandler(registry))

	log.Printf("realtime-gateway starting on :%s (env=%s)", cfg.Port, cfg.Env)
	if err := app.Listen(":" + cfg.Port); err != nil {
		log.Fatalf("server error: %v", err)
	}
}
