
package config

import (
	"fmt"
	"os"
	"strings"

	"github.com/spf13/viper"
)

type Config struct {
	Env      string
	Port     string `mapstructure:"port"`
	LogLevel string `mapstructure:"log_level"`
	RedisAddr   string `mapstructure:"redis_addr"`
	RabbitMQURL string `mapstructure:"rabbitmq_url"`
}

func Load() (*Config, error) {
	env := os.Getenv("APP_ENV")
	if env == "" {
		env = "dev"
	}

	base := viper.New()
	base.SetConfigName("base")
	base.SetConfigType("yaml")
	base.AddConfigPath("./config")
	if err := base.ReadInConfig(); err != nil {
		return nil, fmt.Errorf("read base config: %w", err)
	}

	overlay := viper.New()
	overlay.SetConfigName(env)
	overlay.SetConfigType("yaml")
	overlay.AddConfigPath("./config")
	if err := overlay.ReadInConfig(); err != nil {
		return nil, fmt.Errorf("read %s config: %w", env, err)
	}
	if err := base.MergeConfigMap(overlay.AllSettings()); err != nil {
		return nil, fmt.Errorf("merge %s config: %w", env, err)
	}

	base.SetEnvPrefix("APP")
	base.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	base.AutomaticEnv()


	for _, key := range []string{"redis_addr", "rabbitmq_url"} {
		if err := base.BindEnv(key); err != nil {
			return nil, fmt.Errorf("bind env for %s: %w", key, err)
		}
	}

	var cfg Config
	if err := base.Unmarshal(&cfg); err != nil {
		return nil, fmt.Errorf("unmarshal config: %w", err)
	}
	cfg.Env = env
	return &cfg, nil
}
