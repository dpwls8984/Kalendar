package back.kalender.domain.notification.entity;

import back.kalender.domain.notification.enums.NotificationType;
import back.kalender.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "content", length = 255)
    private String content;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted = false;

    public Notification(Long userId, NotificationType notificationType, String title, String content, Long partyId, Long applicationId) {
        this.userId = userId;
        this.notificationType = notificationType;
        this.title = title;
        this.content = content;
        this.isRead = false;
        this.partyId = partyId;
        this.applicationId = applicationId;
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public void delete() {
        this.isDeleted = true;
    }
}