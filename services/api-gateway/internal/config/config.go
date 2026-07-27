// Package config nạp cấu hình 4 tầng: config/base.yaml (default chung) bị đè bởi
// config/{APP_ENV}.yaml (dev/stg/prod), sau cùng biến môi trường (prefix APP_) đè lên tất cả —
// dùng cho giá trị nhạy cảm (password, connection string) không nằm trong file yaml.
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
