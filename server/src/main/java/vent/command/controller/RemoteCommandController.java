package vent.command.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vent.command.dto.request.BoostRequest;
import vent.command.dto.response.RemoteCommandResponse;
import vent.command.service.BoostRequestException;
import vent.command.service.RemoteCommandService;

/**
 * 원격 명령 API (#8).
 *
 * <pre>
 * POST /api/devices/{장비ID}/boost      부스트 요청 → 201, 상태는 보냄 또는 게이트웨이가 연결되지 않아 보내지 않음
 * GET  /api/devices/{장비ID}/commands   최근 명령 5개
 * </pre>
 * 잘못된 요청(환기장치가 아님, 지원 기능 없음, 기준 밖)은 400과 안내 문구, 없는 장비는 404다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@RestController
@RequestMapping("/api/devices/{deviceId}")
public class RemoteCommandController {

    private final RemoteCommandService commandService;

    public RemoteCommandController(RemoteCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/boost")
    public ResponseEntity<RemoteCommandResponse> requestBoost(@PathVariable String deviceId, @RequestBody BoostRequest request) {
        RemoteCommandResponse command = commandService.requestBoost(deviceId, request, OffsetDateTime.now(ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.CREATED).body(command);
    }

    @GetMapping("/commands")
    public List<RemoteCommandResponse> recentCommands(@PathVariable String deviceId) {
        return commandService.findRecent(deviceId);
    }

    @ExceptionHandler(BoostRequestException.class)
    public ResponseEntity<Map<String, String>> invalidBoost(BoostRequestException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> unknownDevice(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }
}
