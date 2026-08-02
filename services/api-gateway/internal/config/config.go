// Package config loads a 4-layer configuration: config/base.yaml (shared defaults) overridden
// by config/{APP_ENV}.yaml (dev/stg/prod), and finally environment variables (APP_ prefix)
// override everything — for sensitive values (passwords, connection strings) that don't belong
// in a yaml file.
package config

import (
	"fmt"
	"os"
	"strings"

	"github.com/spf13/viper"
)

type Config struct {
	Env            string
	Port           string `mapstructure:"port"`
	LogLevel       string `mapstructure:"log_level"`
	CoreServiceURL string `mapstructure:"core_service_url"`
	RedisHost      string `mapstructure:"redis_host"`
	RedisPort      string `mapstructure:"redis_port"`
	RedisPassword  string `mapstructure:"redis_password"`
	CookieDomain   string `mapstructure:"cookie_domain"`
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

	var cfg Config
	if err := base.Unmarshal(&cfg); err != nil {
		return nil, fmt.Errorf("unmarshal config: %w", err)
	}
	cfg.Env = env
	return &cfg, nil
}
