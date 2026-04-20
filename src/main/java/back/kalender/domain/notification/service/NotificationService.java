package back.kalender.domain.notification.service;

import back.kalender.domain.notification.entity.Notification;
import back.kalender.domain.notification.enums.NotificationType;
import back.kalender.domain.notification.repository.EmitterRepository;
import back.kalender.domain.notification.repository.NotificationRepository;
import back.kalender.domain.notification.response.NotificationResponse;
import back.kalender.domain.party.repository.PartyApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmitterRepository emitterRepository;
    private final NotificationRepository notificationRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60;

    // lastEventId 기준으로 유실된 데이터가 있는지 확인 (클라이언트가 마지막으로 수신한 이벤트 ID)
    public SseEmitter subscribe(Long userId, String lastEventId) {
        // 유저 아이디_시간 -> 다중 연결 지원
        String emitterId = makeTimeIncludeId(userId);
        SseEmitter emitter = emitterRepository.save(emitterId, new SseEmitter(DEFAULT_TIMEOUT));

        emitter.onCompletion(() -> emitterRepository.deleteById(emitterId));
        emitter.onTimeout(() -> emitterRepository.deleteById(emitterId));

        // 503 방지용 더미 이벤트 전송
        String eventId = makeTimeIncludeId(userId);
        sendNotification(emitter, eventId, emitterId, "connect", "EventStream 생성됨. [userId=" + userId + "]");

        // 유실된 event가 있다면 재전송
        if (hasLostData(lastEventId)) {
            sendLostData(lastEventId, userId, emitterId, emitter);
        }

        return emitter;
    }

    // 다른 사용자가 알림을 보낼 수 있는 기능
    @Transactional
    public void send(Long receiverId, NotificationType type, String title, String content, Long partyId, Long applicationId) {
        Notification notification = notificationRepository.save(
                new Notification(receiverId, type, title, content, partyId, applicationId)
        );

        // 누구에게 보낼지 ID 설정
        String receiverIdStr = String.valueOf(receiverId);
        String eventId = receiverIdStr + "_" + System.currentTimeMillis();

        Map<String, SseEmitter> emitters = emitterRepository.findAllEmitterStartWithByMemberId(receiverIdStr);

        String status = null;
        if (type == NotificationType.APPLY && applicationId != null) {
            status = partyApplicationRepository.findById(applicationId)
                    .map(app -> app.getStatus().name())
                    .orElse(null);
        }

        NotificationResponse response = NotificationResponse.from(notification, status);

        emitters.forEach((key, emitter) -> {
            emitterRepository.saveEventCache(key, response);

            sendNotification(emitter, eventId, key, "notification", response);
        });
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findAllByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(notification -> {
                    String status = null;
                    if (notification.getNotificationType() == NotificationType.APPLY && notification.getApplicationId() != null) {
                        status = partyApplicationRepository.findById(notification.getApplicationId())
                                .map(app -> app.getStatus().name())
                                .orElse(null);
                    }
                    return NotificationResponse.from(notification, status);
                });
    }

    @Transactional
    public void readAllNotifications(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private String makeTimeIncludeId(Long userId) {
        return userId + "_" + System.currentTimeMillis();
    }

    private void sendNotification(SseEmitter emitter, String eventId, String emitterId, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(eventId)
                    .name(eventName)
                    .data(data));
        } catch (IOException exception) {
            emitterRepository.deleteById(emitterId);
            log.error("SSE 연결 오류", exception);
        } catch (Exception exception) {
            log.error("알림 전송 중 예상치 못한 에러 발생 (ID: {}): {}", eventId, exception.getMessage());
        }
    }

    // Last-Event-ID가 존재한다는 것은 받지 못한 데이터가 있다는 것
    private boolean hasLostData(String lastEventId) {
        return lastEventId != null && !lastEventId.isEmpty();
    }

    // 받지 못한 데이터가 있다면 Last-Event-ID를 기준으로 그 뒤의 데이터를 추출해 알림을 보내기
    private void sendLostData(String lastEventId, Long userId, String emitterId, SseEmitter emitter) {
        Map<String, Object> eventCaches = emitterRepository.findAllEventCacheStartWithByMemberId(String.valueOf(userId));

        eventCaches.entrySet().stream()
                .filter(entry -> lastEventId.compareTo(entry.getKey()) < 0)
                .forEach(entry -> sendNotification(emitter, entry.getKey(), emitterId, "notification", entry.getValue()));
    }
}