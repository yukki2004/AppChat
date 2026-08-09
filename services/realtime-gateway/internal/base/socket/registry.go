
package socket

import (
	"context"
	"sync"
	"time"

	"github.com/gofiber/contrib/websocket"
	"github.com/redis/go-redis/v9"
)

type Registry struct {
	redis *redis.Client
	mu    sync.RWMutex
	conns map[string]*websocket.Conn
}

func NewRegistry(redisClient *redis.Client) *Registry {
	return &Registry{
		redis: redisClient,
		conns: make(map[string]*websocket.Conn),
	}
}


func (r *Registry) Register(ctx context.Context, redisKey, connID string, conn *websocket.Conn, ttl time.Duration) error {
	r.mu.Lock()
	r.conns[connID] = conn
	r.mu.Unlock()

	return r.redis.Set(ctx, redisKey, connID, ttl).Err()
}


func (r *Registry) Unregister(ctx context.Context, redisKey, connID string) {
	r.mu.Lock()
	delete(r.conns, connID)
	r.mu.Unlock()

	current, err := r.redis.Get(ctx, redisKey).Result()
	if err == nil && current == connID {
		r.redis.Del(ctx, redisKey)
	}
}


func (r *Registry) Resolve(ctx context.Context, redisKey string) (*websocket.Conn, bool, error) {
	connID, err := r.redis.Get(ctx, redisKey).Result()
	if err == redis.Nil {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}

	r.mu.RLock()
	conn, ok := r.conns[connID]
	r.mu.RUnlock()
	return conn, ok, nil
}
