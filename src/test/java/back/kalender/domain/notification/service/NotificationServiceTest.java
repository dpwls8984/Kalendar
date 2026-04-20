package back.kalender.domain.notification.service;

import back.kalender.domain.notification.entity.Notification;
import back.kalender.domain.notification.enums.NotificationType;
import back.kalender.domain.notification.repository.EmitterRepository;
import back.kalender.domain.notification.repository.NotificationRepository;
import back.kalender.domain.notification.response.NotificationResponse;
import back.kalender.domain.party.entity.PartyApplication;
import back.kalender.domain.party.enums.ApplicationStatus;
import back.kalender.domain.party.repository.PartyApplicationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private EmitterRepository emitterRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PartyApplicationRepository partyApplicationRepository;

    @Test
    @DisplayName("알림 구독 성공 (LastEventId 없음)")
    void subscribe_Success() {
        Long userId = 1L;
        String lastEventId = "";

        given(emitterRepository.save(anyString(), any(SseEmitter.class)))
                .willReturn(new SseEmitter());

        SseEmitter result = notificationService.subscribe(userId, lastEventId);

        verify(emitterRepository, times(1)).save(anyString(), any(SseEmitter.class));
    }

    @Test
    @DisplayName("알림 구독 시 유실된 데이터가 있다면 재전송한다 (LastEventId 존재)")
    void subscribe_WithLastEventId_ShouldSendLostData() {
        Long userId = 1L;
        String lastEventId = userId + "_1000"; // 클라이언트는 1000번까지만 받음

        Map<String, Object> events = new ConcurrentHashMap<>();
        events.put(userId + "_1001", "놓친 데이터 1");
        events.put(userId + "_1002", "놓친 데이터 2");

        given(emitterRepository.save(anyString(), any(SseEmitter.class)))
                .willReturn(new SseEmitter());

        given(emitterRepository.findAllEventCacheStartWithByMemberId(String.valueOf(userId)))
                .willReturn(events);

        notificationService.subscribe(userId, lastEventId);

        verify(emitterRepository, times(1)).save(anyString(), any(SseEmitter.class));

        verify(emitterRepository, times(1)).findAllEventCacheStartWithByMemberId(String.valueOf(userId));
    }

    @Test
    @DisplayName("알림 발송 시 연결된 모든 Emitter에게 전송하고 캐시에도 저장한다")
    void send_ShouldBroadcastAndCache() {
        Long userId = 1L;
        String notificationContent = "알림 내용";

        Notification notification = new Notification(userId, NotificationType.APPLY, "제목", notificationContent, null, null);

        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.now());

        Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
        emitters.put(userId + "_1", new SseEmitter());
        emitters.put(userId + "_2", new SseEmitter());

        given(notificationRepository.save(any(Notification.class))).willReturn(notification);
        given(emitterRepository.findAllEmitterStartWithByMemberId(String.valueOf(userId))).willReturn(emitters);

        notificationService.send(userId, NotificationType.APPLY, "제목", notificationContent, null, null);

        verify(notificationRepository, times(1)).save(any(Notification.class));

        verify(emitterRepository, times(2)).saveEventCache(anyString(), any(NotificationResponse.class));

        verify(emitterRepository, times(1)).findAllEmitterStartWithByMemberId(String.valueOf(userId));
    }

    @Test
    @DisplayName("알림 목록 조회 - 리포지토리 결과를 DTO로 변환하여 반환한다")
    void getNotifications_Success() {
        Long userId = 1L;
        Notification n1 = new Notification(userId, NotificationType.APPLY, "T1", "C1", null, null);
        Notification n2 = new Notification(userId, NotificationType.ACCEPT, "T2", "C2", null, null);

        ReflectionTestUtils.setField(n1, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(n2, "createdAt", LocalDateTime.now());

        n2.markAsRead();

        Page<Notification> entityPage = new PageImpl<>(List.of(n1, n2));
        given(notificationRepository.findAllByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(eq(userId), any()))
                .willReturn(entityPage);

        Page<NotificationResponse> result = notificationService.getNotifications(userId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).title()).isEqualTo("T1");
        assertThat(result.getContent().get(0).isRead()).isFalse(); // n1은 안 읽음
        assertThat(result.getContent().get(1).isRead()).isTrue();  // n2는 읽음
    }

    @Test
    @DisplayName("알림 전체 읽음 처리 - 리포지토리의 markAllAsRead를 호출한다")
    void readAllNotifications_Success() {
        Long userId = 1L;

        notificationService.readAllNotifications(userId);

        verify(notificationRepository).markAllAsRead(userId);
    }

    @Test
    @DisplayName("APPLY 타입 알림 조회 시 신청 상태(applicationStatus)가 포함되어야 한다")
    void getNotifications_ShouldIncludeApplicationStatus() {
        Long userId = 1L;
        Long applicationId = 100L;
        Pageable pageable = PageRequest.of(0, 10);

        Notification applyNotification = new Notification(
                userId,
                NotificationType.APPLY,
                "신청 알림",
                "파티 신청했습니다",
                10L,
                applicationId
        );

        ReflectionTestUtils.setField(applyNotification, "createdAt", LocalDateTime.now());

        PartyApplication mockApplication = mock(PartyApplication.class);
        given(mockApplication.getStatus()).willReturn(ApplicationStatus.APPROVED);

        given(notificationRepository.findAllByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(applyNotification)));

        given(partyApplicationRepository.findById(applicationId))
                .willReturn(Optional.of(mockApplication));

        Page<NotificationResponse> result = notificationService.getNotifications(userId, pageable);

        NotificationResponse response = result.getContent().get(0);

        System.out.println("조회된 알림 타입: " + response.notificationType());
        System.out.println("조회된 신청 상태: " + response.applicationStatus());

        assertThat(response.notificationType()).isEqualTo(NotificationType.APPLY);
        assertThat(response.applicationStatus()).isEqualTo("APPROVED");
    }
}