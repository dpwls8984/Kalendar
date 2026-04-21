package back.kalender.global.initData.loadtest;

import back.kalender.domain.user.entity.User;
import back.kalender.domain.user.repository.UserRepository;
import back.kalender.global.common.enums.Gender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("loadtest")
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class UserLoadTestInitData {

    public static final String EMAIL_PREFIX = "loadtest";
    public static final String EMAIL_SUFFIX = "@test.com";
    public static final String PASSWORD_RAW = "password123!";
    public static final int USER_COUNT = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        String encoded = passwordEncoder.encode(PASSWORD_RAW);
        int created = 0;

        for (int i = 1; i <= USER_COUNT; i++) {
            String email = EMAIL_PREFIX + i + EMAIL_SUFFIX;
            if (userRepository.findByEmail(email).isPresent()) {
                continue;
            }

            User user = User.builder()
                    .email(email)
                    .password(encoded)
                    .nickname("부하테스트유저" + i)
                    .level(1)
                    .emailVerified(true)
                    .gender(i % 2 == 0 ? Gender.FEMALE : Gender.MALE)
                    .birthDate(LocalDate.of(
                            1990 + (i % 10),
                            (i % 12) + 1,
                            (i % 28) + 1
                    ))
                    .build();
            userRepository.save(user);
            created++;
        }

        log.info("[LoadTest] User seed: {} created (total loadtest users: {})", created, USER_COUNT);
    }
}
