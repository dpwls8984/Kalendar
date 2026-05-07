package back.kalender.global.initData.schedule;

import back.kalender.domain.artist.entity.Artist;
import back.kalender.domain.artist.repository.ArtistRepository;
import back.kalender.domain.performance.performance.entity.Performance;
import back.kalender.domain.performance.performance.repository.PerformanceRepository;
import back.kalender.domain.performance.schedule.entity.PerformanceSchedule;
import back.kalender.domain.performance.schedule.repository.PerformanceScheduleRepository;
import back.kalender.domain.schedule.entity.Schedule;
import back.kalender.domain.schedule.enums.ScheduleCategory;
import back.kalender.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile({"prod", "dev"})
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class ScheduleBaseInitData implements ApplicationRunner {

    private final ArtistRepository artistRepository;
    private final ScheduleRepository scheduleRepository;
    private final PerformanceRepository performanceRepository;
    private final PerformanceScheduleRepository performanceScheduleRepository;
    private final CacheManager cacheManager;
    private final Random random = new Random();

    private static final List<String> FAN_SIGN_LOCATIONS = List.of(
            "코엑스 라이브플라자", "사운드웨이브 합정", "M2U 레코드 신촌", "여의도 IFC몰", "롯데월드몰 아트리움"
    );

    private static final List<String> CONCERT_LOCATIONS = List.of(
            "KSPO DOME", "잠실실내체육관", "고척 스카이돔", "핸드볼경기장", "장충체육관", "인스파이어 아레나"
    );

    private static final Map<String, String> BROADCASTS = Map.of(
            "SBS 인기가요", "SBS 등촌동 공개홀",
            "KBS 뮤직뱅크", "KBS 신관 공개홀",
            "MBC 쇼! 음악중심", "MBC 상암 공개홀",
            "Mnet 엠카운트다운", "CJ ENM 센터"
    );

    private static final Map<String, String> FESTIVALS = Map.of(
            "2026 고려대 입실렌티", "고려대학교 녹지운동장",
            "2026 연세대 아카라카", "연세대학교 노천극장",
            "워터밤 서울 2026", "킨텍스 야외무대"
    );

    private static final Map<String, String> AWARD_SHOWS = Map.of(
            "MMA 2025", "인스파이어 아레나",
            "2025 MAMA AWARDS", "일본 도쿄돔",
            "제40회 골든디스크", "고척 스카이돔",
            "서울가요대상", "방콕 라자망갈라 스타디움"
    );

    private static final Map<String, LocalDateTime> TICKETING_CONCERTS = Map.of(
            "BTS", LocalDateTime.of(2026, 1, 7, 19, 0),
            "NCT WISH", LocalDateTime.of(2026, 5, 17, 19, 0),
            "aespa", LocalDateTime.of(2026, 6, 13, 19, 0),
            "BLACKPINK", LocalDateTime.of(2026, 7, 11, 19, 0)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Artist> artists = artistRepository.findAll();
        Map<Long, String> artistNameMap = artists.stream()
                .collect(Collectors.toMap(Artist::getId, Artist::getName));

        fixAndDeduplicateConcerts();

        deleteUnwantedConcerts();

        upgradeLegacyData(artistNameMap);

        generateExtendedData(artists);

        manageAnniversaries(artists);

        evictScheduleCaches();

        log.info("Schedule 데이터 정합성 맞춤 및 업데이트 완료");
    }

    private void evictScheduleCaches() {
        for (String cacheName : List.of("scheduleByArtist:monthly", "scheduleByArtist:upcoming", "userAlarms", "artistInfo")) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("[Cache] {} 캐시 전체 비움 (InitData)", cacheName);
            }
        }
    }

    private void fixAndDeduplicateConcerts() {
        TICKETING_CONCERTS.forEach(this::fixSingleConcert);
    }

    private void fixSingleConcert(String artistName, LocalDateTime fixedDate) {
        artistRepository.findByName(artistName).ifPresent(artist -> {
            List<Schedule> concerts = scheduleRepository.findAllByArtistIdAndScheduleCategory(
                    artist.getId(), ScheduleCategory.CONCERT
            );

            Performance performance = performanceRepository.findFirstByArtistId(artist.getId()).orElse(null);
            Long linkedPerformanceId = performance != null ? performance.getId() : null;

            if (concerts.isEmpty()) {
                createDetailSchedule(artist, ScheduleCategory.CONCERT, fixedDate, 0, 0, linkedPerformanceId);
                log.info("{} 콘서트가 없어 새로 생성했습니다. (performanceId={})", artistName, linkedPerformanceId);
            } else {
                Schedule mainConcert = concerts.stream()
                        .filter(s -> s.getPerformanceId() != null)
                        .findFirst()
                        .orElse(concerts.get(0));

                mainConcert.changeScheduleTime(fixedDate);

                DetailInfo info = generateDetailInfo(artistName, ScheduleCategory.CONCERT);
                mainConcert.updateInfo(info.title, info.location);

                if (mainConcert.getPerformanceId() == null && linkedPerformanceId != null) {
                    mainConcert.linkPerformance(linkedPerformanceId);
                    log.info("{} 콘서트에 performanceId={} 링크 복구", artistName, linkedPerformanceId);
                }

                List<Schedule> duplicates = concerts.stream()
                        .filter(s -> !s.getId().equals(mainConcert.getId()))
                        .toList();
                if (!duplicates.isEmpty()) {
                    scheduleRepository.deleteAll(duplicates);
                    log.info("{}의 중복 콘서트 {}개를 삭제했습니다.", artistName, duplicates.size());
                }
            }

            if (performance != null) {
                syncPerformanceWithSchedule(performance, fixedDate.toLocalDate());
            }
        });
    }

    private void syncPerformanceWithSchedule(Performance performance, java.time.LocalDate concertDate) {
        LocalDateTime salesStart = concertDate.minusDays(60).atTime(10, 0);
        LocalDateTime salesEnd = concertDate.minusDays(1).atTime(23, 59);
        performance.updateBookingWindow(concertDate, concertDate, salesStart, salesEnd);

        List<PerformanceSchedule> schedules = performanceScheduleRepository.findByPerformanceId(performance.getId());
        for (PerformanceSchedule ps : schedules) {
            ps.changePerformanceDate(concertDate);
        }
        log.info("Performance(id={}) 날짜 {} 동기화, 회차 {}개 갱신", performance.getId(), concertDate, schedules.size());
    }

    private void deleteUnwantedConcerts() {
        LocalDateTime start = LocalDateTime.of(2026, 2, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 1, 0, 0);

        Set<Long> whitelistedArtistIds = TICKETING_CONCERTS.keySet().stream()
                .map(name -> artistRepository.findByName(name).map(Artist::getId).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Schedule> unwantedConcerts = scheduleRepository.findAllByScheduleTimeBetween(start, end).stream()
                .filter(s -> s.getScheduleCategory() == ScheduleCategory.CONCERT)
                .filter(s -> !whitelistedArtistIds.contains(s.getArtistId()))
                .toList();

        if (!unwantedConcerts.isEmpty()) {
            scheduleRepository.deleteAll(unwantedConcerts);
            log.info("티켓팅 아티스트 외 불필요한 콘서트 데이터 {}건을 삭제했습니다.", unwantedConcerts.size());
        }
    }

    private void upgradeLegacyData(Map<Long, String> artistNameMap) {
        LocalDateTime start = LocalDateTime.of(2025, 12, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 1, 0, 0);

        List<Schedule> legacySchedules = scheduleRepository.findAllByScheduleTimeBetween(start, end);

        for (Schedule schedule : legacySchedules) {
            if (schedule.getTitle() != null && schedule.getTitle().startsWith("[LoadTest]")) {
                continue;
            }
            String artistName = artistNameMap.getOrDefault(schedule.getArtistId(), "Artist");
            DetailInfo info = generateDetailInfo(artistName, schedule.getScheduleCategory());
            schedule.updateInfo(info.title, info.location);
        }
    }

    private void generateExtendedData(List<Artist> artists) {
        LocalDateTime checkDate = LocalDateTime.of(2026, 2, 1, 0, 0);

        for (Artist artist : artists) {
            if (scheduleRepository.existsByArtistIdAndScheduleTimeAfter(artist.getId(), checkDate)) {
                continue;
            }
            createDetailSchedule(artist, ScheduleCategory.BROADCAST, LocalDateTime.of(2026, 2, 1, 18, 0), 0, 40);
            createDetailSchedule(artist, ScheduleCategory.FAN_SIGN, LocalDateTime.of(2026, 2, 15, 19, 0), 0, 60);
            createDetailSchedule(artist, ScheduleCategory.FESTIVAL, LocalDateTime.of(2026, 5, 1, 14, 0), 0, 30);
            createDetailSchedule(artist, ScheduleCategory.ONLINE_RELEASE, LocalDateTime.of(2026, 3, 1, 18, 0), 0, 20);
            createDetailSchedule(artist, ScheduleCategory.LIVE_STREAM, LocalDateTime.of(2026, 2, 10, 22, 0), 0, 100);
            createDetailSchedule(artist, ScheduleCategory.AWARD_SHOW, LocalDateTime.of(2026, 6, 20, 17, 0), 0, 10);
        }
    }

    private void manageAnniversaries(List<Artist> artists) {
        LocalDateTime baseDate = LocalDateTime.of(2025, 12, 1, 0, 0);

        for (Artist artist : artists) {
            List<Schedule> anniversaries = scheduleRepository.findAllByArtistIdAndScheduleCategory(
                    artist.getId(), ScheduleCategory.ANNIVERSARY
            );

            int uniqueDayOffset = (int) ((artist.getId() * 17) % 210);
            LocalDateTime fixedDebutDate = baseDate.plusDays(uniqueDayOffset);

            if (anniversaries.isEmpty()) {
                createDetailSchedule(artist, ScheduleCategory.ANNIVERSARY, fixedDebutDate, 0, 0, null);
            } else {
                Schedule mainAnniversary = anniversaries.get(0);
                mainAnniversary.changeScheduleTime(fixedDebutDate);

                DetailInfo info = generateDetailInfo(artist.getName(), ScheduleCategory.ANNIVERSARY);
                mainAnniversary.updateInfo(info.title, info.location);

                if (anniversaries.size() > 1) {
                    for (int i = 1; i < anniversaries.size(); i++) {
                        scheduleRepository.delete(anniversaries.get(i));
                    }
                }
            }
        }
    }

    private void createDetailSchedule(Artist artist, ScheduleCategory category, LocalDateTime baseDate, int minDay, int maxDay) {
        createDetailSchedule(artist, category, baseDate, minDay, maxDay, null);
    }

    private void createDetailSchedule(Artist artist, ScheduleCategory category, LocalDateTime baseDate, int minDay, int maxDay, Long performanceId) {
        int randomDays = minDay + (maxDay > minDay ? random.nextInt(maxDay - minDay + 1) : 0);
        LocalDateTime eventTime = baseDate.plusDays(randomDays);
        DetailInfo info = generateDetailInfo(artist.getName(), category);

        scheduleRepository.save(
                Schedule.builder()
                        .artistId(artist.getId())
                        .performanceId(performanceId)
                        .scheduleCategory(category)
                        .title(info.title)
                        .scheduleTime(eventTime)
                        .location(info.location)
                        .build()
        );
    }

    private DetailInfo generateDetailInfo(String artistName, ScheduleCategory category) {
        String title = "";
        String location = "";

        switch (category) {
            case BROADCAST -> {
                String broadcastName = getRandomKey(BROADCASTS);
                title = artistName + " " + broadcastName;
                location = BROADCASTS.get(broadcastName);
            }
            case FESTIVAL -> {
                String festName = getRandomKey(FESTIVALS);
                title = artistName + " " + festName;
                location = FESTIVALS.get(festName);
            }
            case AWARD_SHOW -> {
                String awardName = getRandomKey(AWARD_SHOWS);
                title = artistName + " " + awardName;
                location = AWARD_SHOWS.get(awardName);
            }
            case FAN_SIGN -> {
                title = artistName + " 미니앨범 발매 팬사인회";
                location = FAN_SIGN_LOCATIONS.get(random.nextInt(FAN_SIGN_LOCATIONS.size()));
            }
            case FAN_MEETING, CONCERT -> {
                title = artistName + (category == ScheduleCategory.CONCERT ? " 월드 투어 서울" : " 공식 팬미팅");
                location = CONCERT_LOCATIONS.get(random.nextInt(CONCERT_LOCATIONS.size()));
            }
            case ONLINE_RELEASE -> {
                title = artistName + " 디지털 싱글 발매";
                location = null;
            }
            case LIVE_STREAM -> {
                title = artistName + " Weverse Live";
                location = null;
            }
            case ANNIVERSARY, BIRTHDAY -> {
                title = artistName + " 데뷔 기념일";
                location = null;
            }
            default -> {
                title = artistName + " " + category.name();
                location = "스케줄 장소";
            }
        }
        return new DetailInfo(title, location);
    }

    private String getRandomKey(Map<String, String> map) {
        List<String> keys = List.copyOf(map.keySet());
        return keys.get(random.nextInt(keys.size()));
    }

    private record DetailInfo(String title, String location) {}
}