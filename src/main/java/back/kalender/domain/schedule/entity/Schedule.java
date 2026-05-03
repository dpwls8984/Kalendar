package back.kalender.domain.schedule.entity;

import back.kalender.domain.schedule.enums.ScheduleCategory;
import back.kalender.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "schedules",
        indexes = @Index(name = "idx_schedule_artist_time", columnList = "artistId, scheduleTime")
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Schedule extends BaseEntity {

    @Column(nullable = false)
    private Long artistId;

    private Long performanceId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleCategory scheduleCategory;

    private String link;

    @Column(name = "schedule_time")
    private LocalDateTime scheduleTime;

    private String location;

    public void changeScheduleTime(LocalDateTime newTime) {
        this.scheduleTime = newTime;
    }

    public void updateInfo(String title, String location) {
        this.title = title;
        this.location = location;
    }

    public void linkPerformance(Long performanceId) {
        this.performanceId = performanceId;
    }
}
