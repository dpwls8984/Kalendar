package back.kalender.domain.artist.service;

import back.kalender.domain.artist.entity.Artist;
import back.kalender.domain.artist.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistCacheService {

    public static final String CACHE_ARTIST_INFO_ALL = "artistInfo";
    private static final String CACHE_KEY_ALL = "all";

    private final ArtistRepository artistRepository;
    private final CacheManager cacheManager;

    @SuppressWarnings("unchecked")
    public Map<Long, Artist> findAllAsMap() {
        Cache cache = cacheManager.getCache(CACHE_ARTIST_INFO_ALL);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(CACHE_KEY_ALL);
            if (wrapper != null) {
                Object value = wrapper.get();
                if (value instanceof Map<?, ?> map) {
                    return (Map<Long, Artist>) map;
                }
            }
        }

        Map<Long, Artist> result = artistRepository.findAll().stream()
                .collect(Collectors.toMap(Artist::getId, a -> a));

        if (cache != null) {
            cache.put(CACHE_KEY_ALL, result);
        }
        return result;
    }

    public Map<Long, Artist> filterByIds(List<Long> artistIds) {
        Map<Long, Artist> all = findAllAsMap();
        Map<Long, Artist> result = new HashMap<>();
        for (Long id : artistIds) {
            Artist a = all.get(id);
            if (a != null) result.put(id, a);
        }
        return result;
    }

    @CacheEvict(cacheNames = CACHE_ARTIST_INFO_ALL, allEntries = true)
    public void evictAll() {
        log.debug("[Cache] {} evict all", CACHE_ARTIST_INFO_ALL);
    }
}
