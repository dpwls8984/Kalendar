package back.kalender.global.initData.performance;

import back.kalender.domain.artist.entity.Artist;
import back.kalender.domain.artist.repository.ArtistRepository;
import back.kalender.domain.performance.performance.entity.Performance;
import back.kalender.domain.performance.performance.repository.PerformanceRepository;
import back.kalender.domain.performance.schedule.entity.PerformanceSchedule;
import back.kalender.domain.performance.schedule.entity.ScheduleStatus;
import back.kalender.domain.performance.schedule.repository.PerformanceScheduleRepository;
import back.kalender.domain.schedule.entity.Schedule;
import back.kalender.domain.schedule.enums.ScheduleCategory;
import back.kalender.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Profile({"prod", "dev"})
@Order(2)
@RequiredArgsConstructor
public class PerformanceBaseInitData implements ApplicationRunner {

    private final ArtistRepository artistRepository;
    private final PerformanceRepository performanceRepository;
    private final PerformanceScheduleRepository performanceScheduleRepository;
    private final ScheduleRepository scheduleRepository;

    private static final Set<String> TARGET = Set.of(
            "BTS", "G-DRAGON", "aespa", "fromis_9", "NCT WISH"
    );

    private static final Map<String, String> PERFORMANCE_POSTER_URL = Map.of(
            "aespa", "https://wya-kalendar-poster-images-v2.s3.ap-northeast-2.amazonaws.com/aespa.png",
            "BTS", "https://wya-kalendar-poster-images-v2.s3.ap-northeast-2.amazonaws.com/bts.png",
            "fromis_9", "https://wya-kalendar-poster-images-v2.s3.ap-northeast-2.amazonaws.com/fromis9.png",
            "G-DRAGON", "https://wya-kalendar-poster-images-v2.s3.ap-northeast-2.amazonaws.com/gdragon.png",
            "NCT WISH", "https://wya-kalendar-poster-images-v2.s3.ap-northeast-2.amazonaws.com/nctwish.png"
    );

    private static final LocalDate PERFORMANCE_START_DATE = LocalDate.of(2026, 1, 15);
    private static final LocalDate PERFORMANCE_END_DATE   = LocalDate.of(2026, 1, 31);

    @Override
    public void run(ApplicationArguments args) {

        if (performanceRepository.count() > 0) return;

        for (Artist artist : artistRepository.findAll()) {
            if (!TARGET.contains(artist.getName())) continue;
            createPerformance(artist);
        }
    }

    private void createPerformance(Artist artist) {

        LocalDate today = LocalDate.now();

        int range = (int) (
                PERFORMANCE_END_DATE.toEpochDay()
                        - PERFORMANCE_START_DATE.toEpochDay()
                        + 1
        );

        LocalDate performanceDate =
                PERFORMANCE_START_DATE.plusDays((int) (Math.random() * range));

        String posterUrl = PERFORMANCE_POSTER_URL.get(artist.getName());
        Objects.requireNonNull(posterUrl, "공연 포스터 URL 누락: " + artist.getName());

        Performance performance = performanceRepository.save(
                Performance.builder()
                        .performanceHallId(1L)
                        .artistId(artist.getId())
                        .title(artist.getName() + " 단독 콘서트")
                        .posterImageUrl(posterUrl)
                        .startDate(performanceDate)
                        .endDate(performanceDate)
                        .runningTime(120)
                        .bookingNotice("모바일 티켓 입장")
                        .salesStartTime(today.atTime(10, 0))
                        .salesEndTime(today.plusWeeks(2).atTime(23, 59))
                        .build()
        );

        scheduleRepository.save(
                Schedule.builder()
                        .artistId(artist.getId())
                        .performanceId(performance.getId())
                        .scheduleCategory(ScheduleCategory.CONCERT)
                        .title(performance.getTitle())
                        .scheduleTime(performanceDate.atTime(18, 0))
                        .location("잠실실내체육관")
                        .build()
        );

        createSchedule(performance, performanceDate, 1, LocalTime.of(18, 0));
        createSchedule(performance, performanceDate, 2, LocalTime.of(20, 30));
    }

    private void createSchedule(
            Performance performance,
            LocalDate date,
            int no,
            LocalTime time
    ) {
        performanceScheduleRepository.save(
                PerformanceSchedule.builder()
                        .performanceId(performance.getId())
                        .performanceDate(date)
                        .startTime(time)
                        .performanceNo(no)
                        .status(ScheduleStatus.AVAILABLE)
                        .build()
        );
    }
}
