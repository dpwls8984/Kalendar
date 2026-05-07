package back.kalender.domain.artist.service;

import back.kalender.domain.artist.dto.response.ArtistResponse;
import back.kalender.domain.artist.entity.ArtistFollow;
import back.kalender.domain.artist.repository.ArtistFollowRepository;
import back.kalender.domain.artist.repository.ArtistRepository;
import back.kalender.domain.user.repository.UserRepository;
import back.kalender.global.exception.ErrorCode;
import back.kalender.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final ArtistFollowRepository artistFollowRepository;
    private final UserRepository userRepository;

    public List<ArtistResponse> getAllArtists() {
        return artistRepository.findAll().stream()
                .map(ArtistResponse::from)
                .toList();
    }

    public List<ArtistResponse> getAllFollowedArtists(Long userId) {

        List<ArtistFollow> follows = artistFollowRepository.findAllByUserId(userId);

        // artistId만 뽑아서 Artist 조회
        List<Long> artistIds = follows.stream()
                .map(ArtistFollow::getArtistId)
                .toList();

        return artistRepository.findAllById(artistIds).stream()
                .map(ArtistResponse::from)
                .toList();
    }

    @Transactional
    public void followArtist(Long userId, Long artistId) {

        if (!artistRepository.existsById(artistId)) {
            throw new ServiceException(ErrorCode.ARTIST_NOT_FOUND);
        }

        if (artistFollowRepository.existsByUserIdAndArtistId(userId, artistId)) {
            throw new ServiceException(ErrorCode.ALREADY_FOLLOWED);
        }

        artistFollowRepository.save(new ArtistFollow(userId, artistId));
    }

    @Transactional
    public void unfollowArtist(Long userId, Long artistId) {

        if (!artistFollowRepository.existsByUserIdAndArtistId(userId, artistId)) {
            throw new ServiceException(ErrorCode.ARTIST_NOT_FOLLOWED);
        }

        artistFollowRepository.deleteByUserIdAndArtistId(userId, artistId);
    }

}

