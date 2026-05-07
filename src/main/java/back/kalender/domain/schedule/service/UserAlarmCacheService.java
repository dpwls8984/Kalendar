package back.kalender.domain.schedule.service;

import back.kalender.domain.schedule.repository.ScheduleAlarmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAlarmCacheService {

    public static final String CACHE_USER_ALARMS = "userAlarms";

    private final ScheduleAlarmRepository scheduleAlarmRepository;

    @Cacheable(cacheNames = CACHE_USER_ALARMS, key = "#userId")
    public Set<Long> findAlarmedScheduleIds(Long userId) {
        return scheduleAlarmRepository.findScheduleIdsByUserId(userId);
    }

    @CacheEvict(cacheNames = CACHE_USER_ALARMS, key = "#userId")
    public void evict(Long userId) {
        log.debug("[Cache] {} evict for userId={}", CACHE_USER_ALARMS, userId);
    }
}
