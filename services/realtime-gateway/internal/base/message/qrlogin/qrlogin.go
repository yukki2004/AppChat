
package qrlogin

import "time"

const TTL = 90 * time.Second

func RedisKey(qrToken string) string {
	return "cache:ws:qr_login:" + qrToken
}


type ApprovedPublish struct {
	Type string `json:"type"`
}

type ExpiredPublish struct {
	Type string `json:"type"`
}

var Approved = ApprovedPublish{Type: "qr_login.approved"}
var Expired = ExpiredPublish{Type: "qr_login.expired"}
