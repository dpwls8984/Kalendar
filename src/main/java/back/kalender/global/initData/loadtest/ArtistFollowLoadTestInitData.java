package back.kalender.global.initData.loadtest;

import back.kalender.domain.artist.entity.Artist;
import back.kalender.domain.artist.entity.ArtistFollow;
import back.kalender.domain.artist.repository.ArtistFollowRepository;
import back.kalender.domain.artist.repository.ArtistRepository;
import back.kalender.domain.user.entity.User;
import back.kalender.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
@Profile("loadtest")
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class ArtistFollowLoadTestInitData implements ApplicationRunner {

    private static final int MIN_FOLLOW = 2;
    private static final int MAX_FOLLOW = 10;
    private static final long SEED = 42L;

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final ArtistFollowRepository artistFollowRepository;

    @Override
    public void run(ApplicationArguments args) {
        Random random = new Random(SEED);

        List<User> loadtestUsers = userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().startsWith(UserLoadTestInitData.EMAIL_PREFIX))
                .toList();

        List<Artist> artists = artistRepository.findAll();
        if (artists.isEmpty() || loadtestUsers.isEmpty()) {
            log.warn("[LoadTest] Artists={} / loadtestUsers={} — follow seed skipped",
                    artists.size(), loadtestUsers.size());
            return;
        }

        int created = 0;
        for (User user : loadtestUsers) {
            List<ArtistFollow> existing = artistFollowRepository.findAllByUserId(user.getId());
            if (!existing.isEmpty()) {
                continue;
            }

            int followCount = MIN_FOLLOW + random.nextInt(MAX_FOLLOW - MIN_FOLLOW + 1);
            List<Artist> shuffled = new ArrayList<>(artists);
            Collections.shuffle(shuffled, random);

            for (int i = 0; i < followCount && i < shuffled.size(); i++) {
                artistFollowRepository.save(new ArtistFollow(user.getId(), shuffled.get(i).getId()));
                created++;
            }
        }

        log.info("[LoadTest] ArtistFollow seed: {} follows created across {} loadtest users",
                created, loadtestUsers.size());
    }
}
