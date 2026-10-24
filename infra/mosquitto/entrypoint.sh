#!/bin/sh
# 브로커 시작 스크립트: 환경변수로 비밀번호 파일을 만든 뒤 Mosquitto를 띄운다 (#13).
# @author predevho (AI 보조: Claude)
# @since 2026-10-24 (#13)
#
# 비밀번호(해시 포함)를 저장소에 두지 않기 위해, 시작할 때마다 새로 만든다.
# 기본값은 시연용이다. 운영에서는 비밀 관리 서비스에서 주입하고 장비별 인증서(TLS 클라이언트 인증)를 쓴다.
set -eu

RUN_DIR=/mosquitto/run
PASSWD_FILE=$RUN_DIR/passwd
ACL_FILE=$RUN_DIR/acl

# 새로 만드는 파일은 처음부터 소유자만 읽고 쓰게 한다. Mosquitto 2는 누구나 읽을 수 있는 비밀번호·ACL 파일에 경고하고,
# 다음 버전부터는 거부한다.
umask 077
mkdir -p "$RUN_DIR"
rm -f "$PASSWD_FILE" "$ACL_FILE"
touch "$PASSWD_FILE"

add_user() {
    mosquitto_passwd -b "$PASSWD_FILE" "$1" "$2"
}

add_user vent-server "${MQTT_PASSWORD_VENT_SERVER:-vent-server-demo}"
add_user gw-s1 "${MQTT_PASSWORD_GW_S1:-gw-s1-demo}"
add_user gw-s2 "${MQTT_PASSWORD_GW_S2:-gw-s2-demo}"
add_user gw-s3 "${MQTT_PASSWORD_GW_S3:-gw-s3-demo}"

# ACL은 저장소 파일(읽기 전용 마운트)을 복사해 권한을 좁힌다.
cp /mosquitto/config/acl "$ACL_FILE"

# Mosquitto는 mosquitto 사용자(1883)로 실행된다. 두 파일은 그 사용자만 읽게 한다.
chown 1883:1883 "$RUN_DIR" "$PASSWD_FILE" "$ACL_FILE"
chmod 0600 "$PASSWD_FILE" "$ACL_FILE"

exec /docker-entrypoint.sh /usr/sbin/mosquitto -c /mosquitto/config/mosquitto.conf
