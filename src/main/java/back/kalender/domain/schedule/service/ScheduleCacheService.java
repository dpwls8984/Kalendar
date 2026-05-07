package back.kalender.domain.schedule.service;

import back.kalender.domain.schedule.entity.Schedule;
import back.kalender.domain.schedule.repository.ScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ScheduleCacheService {

    public static final String CACHE_MONTHLY = "scheduleByArtist:monthly";
    public static final String CACHE_UPCOMING = "scheduleByArtist:upcoming";

    private static final int UPCOMING_FETCH_SIZE = 10;

    private final ScheduleRepository scheduleRepository;
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> jsonRedisTemplate;

    public ScheduleCacheService(
            ScheduleRepository scheduleRepository,
            CacheManager cacheManager,
            @Qualifier("jsonRedisTemplate") RedisTemplate<String, Object> jsonRedisTemplate
    ) {
        this.scheduleRepository = scheduleRepository;
        this.cacheManager = cacheManager;
        this.jsonRedisTemplate = jsonRedisTemplate;
    }

    @Cacheable(cacheNames = CACHE_MONTHLY, key = "#artistId + ':' + #year + '-' + #month")
    public List<Schedule> findMonthlyByArtist(Long artistId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.atEndOfMonth().atTime(LocalTime.MAX);
        return scheduleRepository.findMonthlySchedules(List.of(artistId), start, end);
    }

    @Cacheable(cacheNames = CACHE_UPCOMING, key = "#artistId")
    public List<Schedule> findUpcomingByArtist(Long artistId) {
        return scheduleRepository.findUpcomingEvents(
                List.of(artistId), LocalDateTime.now(), PageRequest.of(0, UPCOMING_FETCH_SIZE)
        );
    }

    public List<Schedule> findMonthlyByArtists(List<Long> artistIds, int year, int month) {
        if (artistIds == null || artistIds.isEmpty()) return Collections.emptyList();

        List<String> redisKeys = new ArrayList<>(artistIds.size());
        for (Long id : artistIds) {
            redisKeys.add(CACHE_MONTHLY + "::" + id + ":" + year + "-" + month);
        }

        List<Object> cached = jsonRedisTemplate.opsForValue().multiGet(redisKeys);

        List<Schedule> aggregated = new ArrayList<>();
        List<Long> missArtistIds = new ArrayList<>();

        for (int i = 0; i < artistIds.size(); i++) {
            Object value = cached != null ? cached.get(i) : null;
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Schedule s) aggregated.add(s);
                }
            } else {
                missArtistIds.add(artistIds.get(i));
            }
        }

        if (!missArtistIds.isEmpty()) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime end = yearMonth.atEndOfMonth().atTime(LocalTime.MAX);
            List<Schedule> dbResults = scheduleRepository.findMonthlySchedules(missArtistIds, start, end);

            Map<Long, List<Schedule>> byArtist = new HashMap<>();
            for (Long id : missArtistIds) byArtist.put(id, new ArrayList<>());
            for (Schedule s : dbResults) {
                byArtist.computeIfAbsent(s.getArtistId(), k -> new ArrayList<>()).add(s);
            }

            Cache cache = cacheManager.getCache(CACHE_MONTHLY);
            for (Map.Entry<Long, List<Schedule>> e : byArtist.entrySet()) {
                String cacheKey = e.getKey() + ":" + year + "-" + month;
                if (cache != null) cache.put(cacheKey, e.getValue());
                aggregated.addAll(e.getValue());
            }
        }

        aggregated.sort((a, b) -> a.getScheduleTime().compareTo(b.getScheduleTime()));
        return aggregated;
    }

    public List<Schedule> findUpcomingByArtists(List<Long> artistIds) {
        if (artistIds == null || artistIds.isEmpty()) return Collections.emptyList();

        List<String> redisKeys = new ArrayList<>(artistIds.size());
        for (Long id : artistIds) {
            redisKeys.add(CACHE_UPCOMING + "::" + id);
        }

        List<Object> cached = jsonRedisTemplate.opsForValue().multiGet(redisKeys);

        List<Schedule> aggregated = new ArrayList<>();
        List<Long> missArtistIds = new ArrayList<>();

        for (int i = 0; i < artistIds.size(); i++) {
            Object value = cached != null ? cached.get(i) : null;
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Schedule s) aggregated.add(s);
                }
            } else {
                missArtistIds.add(artistIds.get(i));
            }
        }

        if (!missArtistIds.isEmpty()) {
            Cache cache = cacheManager.getCache(CACHE_UPCOMING);
            for (Long id : missArtistIds) {
                List<Schedule> single = scheduleRepository.findUpcomingEvents(
                        List.of(id), LocalDateTime.now(), PageRequest.of(0, UPCOMING_FETCH_SIZE)
                );
                if (cache != null) cache.put(id, single);
                aggregated.addAll(single);
            }
        }

        aggregated.sort((a, b) -> a.getScheduleTime().compareTo(b.getScheduleTime()));
        return aggregated.size() > 10 ? aggregated.subList(0, 10) : aggregated;
    }

    @CacheEvict(cacheNames = {CACHE_MONTHLY, CACHE_UPCOMING}, allEntries = true)
    public void evictAll() {
        log.info("[Cache] {} / {} 전체 비움", CACHE_MONTHLY, CACHE_UPCOMING);
    }
}
