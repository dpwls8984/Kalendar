package back.kalender.domain.notification.repository;

import back.kalender.domain.notification.entity.Notification;
import back.kalender.domain.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 삭제되지 않은 알림만 조회하는 메서드
    Page<Notification> findAllByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 신청 취소 시 기존에 보냈던 'APPLY' 알림을 찾기 위한 메서드
    Optional<Notification> findByApplicationIdAndNotificationType(Long applicationId, NotificationType notificationType);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsRead(@Param("userId") Long userId);
}
