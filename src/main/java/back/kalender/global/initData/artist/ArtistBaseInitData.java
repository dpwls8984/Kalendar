package back.kalender.global.initData.artist;

import back.kalender.domain.artist.entity.Artist;
import back.kalender.domain.artist.repository.ArtistRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile({"prod", "dev"})
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class ArtistBaseInitData {

    private final ArtistRepository artistRepository;

    @PostConstruct
    public void init() {
        if (artistRepository.count() > 0) {
            log.info("Artist base data already initialized");
            return;
        }
        createArtists();
    }

    private void createArtists() {
        List<ArtistSeed> artists = List.of(
            new ArtistSeed("aespa", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/aespa.png"),
            new ArtistSeed("ALLDAY PROJECT", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/ALLDAY PROJECT.png"),
            new ArtistSeed("ATEEZ", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/ATEEZ.png"),
            new ArtistSeed("BABYMONSTER", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/BABYMONSTER.png"),
            new ArtistSeed("BLACKPINK", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/BLACKPINK.png"),
            new ArtistSeed("BOYNEXTDOOR", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/BOYNEXTDOOR.png"),
            new ArtistSeed("BTOB", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/BTOB.png"),
            new ArtistSeed("BTS", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/BTS.png"),
            new ArtistSeed("DAY6", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/DAY6.png"),
            new ArtistSeed("ENHYPEN", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/ENHYPEN.png"),
            new ArtistSeed("EXO", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/EXO.png"),
            new ArtistSeed("fromis_9", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/fromis_9.png"),
            new ArtistSeed("G-DRAGON", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/G-DRAGON.png"),
            new ArtistSeed("Girls' Generation", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/Girls' Generation.png"),
            new ArtistSeed("Hearts2Hearts", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/Hearts2Hearts.png"),
            new ArtistSeed("i-dle", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/i-dle.png"),
            new ArtistSeed("ILLIT", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/ILLIT.png"),
            new ArtistSeed("ITZY", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/ITZY.png"),
            new ArtistSeed("IVE", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/ive.png"),
            new ArtistSeed("izna", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/izna.png"),
            new ArtistSeed("KiiiKiii", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/KiiiKiii.png"),
            new ArtistSeed("KISS OF LIFE", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/KISS OF LIFE.png"),
            new ArtistSeed("LE SSERAFIM", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/LE SSERAFIM.png"),
            new ArtistSeed("MEOVV", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/MEOVV.png"),
            new ArtistSeed("MONSTA X", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/MONSTA X.png"),
            new ArtistSeed("NCT 127", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/NCT 127.png"),
            new ArtistSeed("NCT DREAM", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/NCT DREAM.png"),
            new ArtistSeed("NCT WISH", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/NCT WISH.png"),
            new ArtistSeed("NewJeans", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/NewJeans.png"),
            new ArtistSeed("NMIXX", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/NMIXX.png"),
            new ArtistSeed("P1Harmony", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/P1Harmony.png"),
            new ArtistSeed("Red Velvet", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/Red Velvet.png"),
            new ArtistSeed("RIIZE", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/RIIZE.png"),
            new ArtistSeed("SEVENTEEN", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/SEVENTEEN.png"),
            new ArtistSeed("SHINee", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/SHINEE.png"),
            new ArtistSeed("STAYC", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/STAYC.png"),
            new ArtistSeed("Stray Kids", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/Stray Kids.png"),
            new ArtistSeed("SUPER JUNIOR", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/SUPER JUNIOR.png"),
            new ArtistSeed("THE BOYZ", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/THE BOYZ.png"),
            new ArtistSeed("TREASURE", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/TREASURE.png"),
            new ArtistSeed("tripleS", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/tripleS.png"),
            new ArtistSeed("TVXQ!", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/TVXQ!.png"),
            new ArtistSeed("TWICE", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/TWICE.png"),
            new ArtistSeed("TWS", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/TWS.png"),
            new ArtistSeed("TXT", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/TXT.png"),
            new ArtistSeed("VIVIZ", "https://wya-kalendar-artist-logos-v2.s3.ap-northeast-2.amazonaws.com/VIVIZ.png")
        );

        for (ArtistSeed seed : artists) {
            if (artistRepository.existsByName(seed.name())) {
                continue;
            }
            artistRepository.save(
                new Artist(seed.name(), seed.imageUrl())
            );
        }

        log.info("Artist base data initialized: {} artists", artists.size());
    }

    private record ArtistSeed(String name, String imageUrl) {}
}