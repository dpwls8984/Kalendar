package back.kalender.domain.schedule.service;

import back.kalender.domain.artist.entity.Artist;
import back.kalender.domain.artist.entity.ArtistFollow;
import back.kalender.domain.artist.repository.ArtistFollowRepository;
import back.kalender.domain.artist.repository.ArtistRepository;
import back.kalender.domain.schedule.dto.response.*;
import back.kalender.domain.schedule.entity.Schedule;
import back.kalender.domain.schedule.entity.ScheduleAlarm;
import back.kalender.domain.schedule.enums.ScheduleCategory;
import back.kalender.domain.schedule.mapper.ScheduleResponseMapper;
import back.kalender.domain.schedule.repository.ScheduleAlarmRepository;
import back.kalender.domain.schedule.repository.ScheduleRepository;
import back.kalender.global.exception.ErrorCode;
import back.kalender.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ArtistFollowRepository artistFollowRepository;
    private final ArtistRepository artistRepository;
    private final ScheduleAlarmRepository scheduleAlarmRepository;

    private static final int PARTY_AVAILABLE_DAYS = 31;
    private static final List<ScheduleCategory> PARTY_CATEGORIES = List.of(
            ScheduleCategory.CONCERT,
            ScheduleCategory.FAN_MEETING,
            ScheduleCategory.FESTIVAL,
            ScheduleCategory.AWARD_SHOW,
            ScheduleCategory.FAN_SIGN,
            ScheduleCategory.BROADCAST
    );

    private List<Long> resolveTargetArtistIdsForGuest(
            List<Long> artistIds,
            Long artistId
    ) {
        if (artistId != null) {
            if (!artistIds.contains(artistId)) {
                log.warn("[Schedule][Guest] 필터 artistId={} 가 artistIds에 없음", artistId);
                return Collections.emptyList();
            }
            return List.of(artistId);
        }
        return artistIds;
    }

    @Cacheable(
            cacheNames = "scheduleByArtists",
            condition = "#artistIds != null && !#artistIds.isEmpty()",
            key = "#year + '-' + #month + ':' + #artistIds.stream().sorted().distinct().toList() + ':' + (#artistId != null ? #artistId : 'all')"
    )
    public FollowingSchedulesListResponse getSchedulesByArtistIds(
            int year,
            int month,
            List<Long> artistIds,
            Long artistId
    ) {
        validateMonth(month);

        if (artistIds == null || artistIds.isEmpty()) {
            log.info("[Schedule][Guest] 선택된 아티스트 없음 → 빈 결과 반환");
            return new FollowingSchedulesListResponse(
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        List<Long> targetArtistIds = resolveTargetArtistIdsForGuest(artistIds, artistId);
        if (targetArtistIds.isEmpty()) {
            return new FollowingSchedulesListResponse(
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        List<Schedule> monthlySchedules = fetchMonthlySchedules(targetArtistIds, year, month);
        List<Schedule> upcomingSchedules = fetchUpcomingSchedules(targetArtistIds);

        log.info("[Schedule][Guest] DB 조회 결과 - monthly: {}건, upcoming: {}건",
                monthlySchedules.size(), upcomingSchedules.size());

        Map<Long, Artist> artistMap = buildArtistMap(monthlySchedules, upcomingSchedules);

        List<ScheduleResponse> monthlyResponses =
                mapToMonthlyResponses(monthlySchedules, artistMap, Collections.emptySet());

        List<UpcomingEventResponse> upcomingResponses =
                mapToUpcomingResponses(upcomingSchedules, artistMap, Collections.emptySet());

        return new FollowingSchedulesListResponse(monthlyResponses, upcomingResponses);
    }

    @Cacheable(
            cacheNames = "scheduleFollowing",
            key = "#userId + ':' + #year + '-' + #month + ':' + (#artistId != null ? #artistId : 'all')"
    )
    public FollowingSchedulesListResponse getFollowingSchedules(
            Long userId, int year, int month, Long artistId
    ) {
        validateMonth(month);

        List<Long> targetArtistIds = resolveTargetArtistIds(userId, artistId);
        if (targetArtistIds.isEmpty()) {
            log.info("[Schedule] 팔로우한 아티스트가 없어 빈 결과 반환 - userId={}", userId);
            return new FollowingSchedulesListResponse(Collections.emptyList(), Collections.emptyList());
        }

        List<Schedule> monthlySchedules = fetchMonthlySchedules(targetArtistIds, year, month);
        List<Schedule> upcomingSchedules = fetchUpcomingSchedules(targetArtistIds);
        log.info("[Schedule] DB 조회 결과 - monthly: {}건, upcoming: {}건", monthlySchedules.size(), upcomingSchedules.size());

        Set<Long> alarmedScheduleIds = scheduleAlarmRepository.findScheduleIdsByUserId(userId);

        Map<Long, Artist> artistMap = buildArtistMap(monthlySchedules, upcomingSchedules);

        List<ScheduleResponse> monthlyResponses = mapToMonthlyResponses(monthlySchedules, artistMap, alarmedScheduleIds);
        List<UpcomingEventResponse> upcomingResponses = mapToUpcomingResponses(upcomingSchedules, artistMap, alarmedScheduleIds);

        return new FollowingSchedulesListResponse(monthlyResponses, upcomingResponses);
    }

    public EventsListResponse getEventLists(Long userId) {
        List<Long> targetArtistIds = getFollowedArtistIds(userId);
        if (targetArtistIds.isEmpty()) {
            return new EventsListResponse(Collections.emptyList());
        }

        List<Schedule> schedules = fetchPartyAvailableEvents(targetArtistIds);

        Map<Long, Artist> artistMap = buildArtistMap(schedules, Collections.emptyList());

        List<EventResponse> items = schedules.stream()
                .map(schedule -> {
                    Artist artist = artistMap.get(schedule.getArtistId());

                    if (artist == null) {
                        log.warn("[Schedule] 데이터 불일치 감지 - Schedule ID: {}의 Artist ID: {}를 찾을 수 없음",
                                schedule.getId(), schedule.getArtistId());
                        return null;
                    }

                    return ScheduleResponseMapper.toEventResponse(schedule, artist);
                })
                .filter(Objects::nonNull)
                .toList();

        return new EventsListResponse(items);
    }

    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            log.error("[Schedule] 유효하지 않은 월 입력: {}", month);
            throw new ServiceException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<Long> resolveTargetArtistIds(Long userId, Long artistId) {
        if (artistId != null) {
            if (!artistFollowRepository.existsByUserIdAndArtistId(userId, artistId)) {
                throw new ServiceException(ErrorCode.ARTIST_NOT_FOLLOWED);
            }
            return List.of(artistId);
        }
        return getFollowedArtistIds(userId);
    }

    private List<Schedule> fetchMonthlySchedules(List<Long> artistIds, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.atEndOfMonth().atTime(LocalTime.MAX);
        return scheduleRepository.findMonthlySchedules(artistIds, start, end);
    }

    private List<Schedule> fetchUpcomingSchedules(List<Long> artistIds) {
        return scheduleRepository.findUpcomingEvents(
                artistIds, LocalDateTime.now(), PageRequest.of(0, 10)
        );
    }

    private List<Schedule> fetchPartyAvailableEvents(List<Long> artistIds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusDays(PARTY_AVAILABLE_DAYS);
        return scheduleRepository.findPartyAvailableEvents(artistIds, PARTY_CATEGORIES, now, endDate);
    }
    private Map<Long, Artist> buildArtistMap(List<Schedule> list1, List<Schedule> list2) {
        List<Long> allIds = Stream.concat(
                list1.stream().map(Schedule::getArtistId),
                list2.stream().map(Schedule::getArtistId)
        ).distinct().toList();

        return artistRepository.findAllById(allIds).stream()
                .collect(Collectors.toMap(Artist::getId, artist -> artist));
    }

    private List<ScheduleResponse> mapToMonthlyResponses(List<Schedule> schedules, Map<Long, Artist> artistMap, Set<Long> alarmedScheduleIds) {
        return schedules.stream()
                .map(schedule -> {
                    Artist artist = artistMap.get(schedule.getArtistId());
                    boolean isAlarmOn = alarmedScheduleIds.contains(schedule.getId());

                    return ScheduleResponseMapper.toScheduleResponse(schedule, artist, isAlarmOn);
                })
                .toList();
    }

    private List<UpcomingEventResponse> mapToUpcomingResponses(List<Schedule> schedules, Map<Long, Artist> artistMap, Set<Long> alarmedScheduleIds) {
        LocalDate today = LocalDate.now();
        return schedules.stream()
                .map(schedule -> {
                    Artist artist = artistMap.get(schedule.getArtistId());
                    boolean isAlarmOn = alarmedScheduleIds.contains(schedule.getId());
                    return ScheduleResponseMapper.toUpcomingEventResponse(schedule, artist, today, isAlarmOn);
                })
                .toList();
    }

    private List<Long> getFollowedArtistIds(Long userId) {
        List<ArtistFollow> follows = artistFollowRepository.findAllByUserId(userId);
        if (follows.isEmpty()) return Collections.emptyList();

        return follows.stream()
                .map(ArtistFollow::getArtistId)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "scheduleFollowing", allEntries = true)
    public void toggleScheduleAlarm(Long userId, Long scheduleId) {
        scheduleAlarmRepository.findByScheduleIdAndUserId(scheduleId, userId)
                .ifPresentOrElse(
                        scheduleAlarmRepository::delete,
                        () -> scheduleAlarmRepository.save(new ScheduleAlarm(scheduleId, userId))
                );
    }
}