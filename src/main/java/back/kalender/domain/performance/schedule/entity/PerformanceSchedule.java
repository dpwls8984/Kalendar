package back.kalender.domain.performance.schedule.entity;

import back.kalender.domain.performance.performance.entity.Performance;
import back.kalender.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "performance_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PerformanceSchedule extends BaseEntity {
    private Long performanceId;

    @Column(name = "performance_date", nullable = false)
    private LocalDate performanceDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name="performance_no")
    private Integer performanceNo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ScheduleStatus status;

    public void changePerformanceDate(LocalDate performanceDate) {
        this.performanceDate = performanceDate;
    }
}
