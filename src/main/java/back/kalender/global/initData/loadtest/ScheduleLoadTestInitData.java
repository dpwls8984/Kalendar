package back.kalender.global.initData.loadtest;

import back.kalender.domain.artist.entity.Artist;
import back.kalender.domain.artist.repository.ArtistRepository;
import back.kalender.domain.schedule.entity.Schedule;
import back.kalender.domain.schedule.enums.ScheduleCategory;
import back.kalender.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Random;

@Component
@Profile("loadtest")
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class ScheduleLoadTestInitData implements ApplicationRunner {

    private static final String LOADTEST_TITLE_PREFIX = "[LoadTest]";
    private static final int PER_ARTIST_PER_MONTH = 20;
    private static final YearMonth START_MONTH = YearMonth.of(2026, 4);
    private static final YearMonth END_MONTH = YearMonth.of(2026, 12);
    private static final long SEED = 7L;

    private static final List<ScheduleCategory> CATEGORIES = List.of(
            ScheduleCategory.BROADCAST,
            ScheduleCategory.LIVE_STREAM,
            ScheduleCategory.FAN_SIGN,
            ScheduleCategory.ONLINE_RELEASE
    );

    private final ArtistRepository artistRepository;
    private final ScheduleRepository scheduleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Artist> artists = artistRepository.findAll();
        if (artists.isEmpty()) {
            log.warn("[LoadTest] No artists found — schedule seed skipped");
            return;
        }

        Random random = new Random(SEED);
        int created = 0;

        for (Artist artist : artists) {
            for (YearMonth ym = START_MONTH; !ym.isAfter(END_MONTH); ym = ym.plusMonths(1)) {
                long existingInMonth = countLoadTestSchedulesInMonth(artist.getId(), ym);
                int toCreate = Math.max(0, PER_ARTIST_PER_MONTH - (int) existingInMonth);

                for (int i = 0; i < toCreate; i++) {
                    ScheduleCategory category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
                    int day = 1 + random.nextInt(ym.lengthOfMonth());
                    int hour = 10 + random.nextInt(11);

                    Schedule schedule = Schedule.builder()
                            .artistId(artist.getId())
                            .scheduleCategory(category)
                            .title(LOADTEST_TITLE_PREFIX + " " + artist.getName() + " " + category.name())
                            .scheduleTime(LocalDateTime.of(ym.getYear(), ym.getMonth(), day, hour, 0))
                            .location(null)
                            .build();
                    scheduleRepository.save(schedule);
                    created++;
                }
            }
        }

        log.info("[LoadTest] Schedule seed: {} schedules created across {} artists (months {}~{}, target {}/month)",
                created, artists.size(), START_MONTH, END_MONTH, PER_ARTIST_PER_MONTH);
    }

    private long countLoadTestSchedulesInMonth(Long artistId, YearMonth ym) {
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        long total = 0;
        for (ScheduleCategory category : CATEGORIES) {
            total += scheduleRepository.findAllByArtistIdAndScheduleCategory(artistId, category)
                    .stream()
                    .filter(s -> s.getTitle() != null && s.getTitle().startsWith(LOADTEST_TITLE_PREFIX))
                    .filter(s -> !s.getScheduleTime().isBefore(start) && s.getScheduleTime().isBefore(end))
                    .count();
        }
        return total;
    }
}
