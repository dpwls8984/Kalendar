package back.kalender.domain.party.service;

import back.kalender.domain.chat.service.ChatRoomService;
import back.kalender.domain.notification.entity.Notification;
import back.kalender.domain.notification.enums.NotificationType;
import back.kalender.domain.notification.repository.NotificationRepository;
import back.kalender.domain.notification.service.NotificationService;
import back.kalender.domain.party.dto.request.CreatePartyRequest;
import back.kalender.domain.party.dto.request.UpdatePartyRequest;
import back.kalender.domain.party.dto.response.*;
import back.kalender.domain.party.entity.Party;
import back.kalender.domain.party.entity.PartyApplication;
import back.kalender.domain.party.entity.PartyMember;
import back.kalender.domain.party.enums.*;
import back.kalender.domain.party.mapper.PartyBuilder;
import back.kalender.domain.party.repository.PartyApplicationRepository;
import back.kalender.domain.party.repository.PartyMemberRepository;
import back.kalender.domain.party.repository.PartyRepository;
import back.kalender.domain.party.repository.PartyRepositoryCustom;
import back.kalender.domain.schedule.entity.Schedule;
import back.kalender.domain.schedule.repository.ScheduleRepository;
import back.kalender.domain.user.entity.User;
import back.kalender.domain.user.repository.UserRepository;
import back.kalender.global.exception.ErrorCode;
import back.kalender.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyService {

    private static final int MAX_COMPLETED_PARTIES_FETCH = 1000;

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final NotificationService notificationService;
    private final ChatRoomService chatRoomService;
    private final NotificationRepository notificationRepository;


    @Transactional
    public CreatePartyResponse createParty(CreatePartyRequest request, Long currentUserId) {
        log.info("[파티 생성] userId={}, scheduleId={}, partyName={}",
                currentUserId, request.scheduleId(), request.partyName());

        validateScheduleExists(request.scheduleId());

        Party party = PartyBuilder.create(request, currentUserId);
        partyRepository.save(party);

        partyMemberRepository.save(PartyMember.createLeader(party.getId(), currentUserId));
        chatRoomService.createChatRoom(party.getId(), party.getPartyName());

        log.info("[파티 생성 완료] partyId={}", party.getId());
        return new CreatePartyResponse(party.getId(), party.getLeaderId(), "생성 완료");
    }

    @Transactional
    public UpdatePartyResponse updateParty(Long partyId, UpdatePartyRequest request, Long currentUserId) {
        log.info("[파티 수정] partyId={}, userId={}", partyId, currentUserId);

        Party party = getPartyOrThrow(partyId);
        validateLeaderPermission(party, currentUserId, ErrorCode.CANNOT_MODIFY_PARTY_NOT_LEADER);
        validateMaxMembersUpdate(request.maxMembers(), party.getCurrentMembers());

        PartyBuilder.update(request, party);

        log.info("[파티 수정 완료] partyId={}", partyId);
        return new UpdatePartyResponse(party.getId(), party.getLeaderId(), "수정 완료");
    }

    @Transactional
    public ClosePartyResponse closeParty(Long partyId, Long currentUserId) {
        log.info("[파티 모집 마감] partyId={}, userId={}", partyId, currentUserId);

        Party party = getPartyOrThrow(partyId);
        validateLeaderPermission(party, currentUserId, ErrorCode.CANNOT_MODIFY_PARTY_NOT_LEADER);
        validatePartyRecruiting(party);

        party.changeStatus(PartyStatus.CLOSED);

        log.info("[파티 모집 마감 완료] partyId={}, currentMembers={}/{}",
                partyId, party.getCurrentMembers(), party.getMaxMembers());
        return new ClosePartyResponse(partyId, "모집이 마감되었습니다.");
    }

    @Transactional
    public void deleteParty(Long partyId, Long currentUserId) {
        log.info("[파티 삭제] partyId={}, userId={}", partyId, currentUserId);

        Party party = getPartyOrThrow(partyId);
        validateLeaderPermission(party, currentUserId, ErrorCode.CANNOT_DELETE_PARTY_NOT_LEADER);

        chatRoomService.closeChatRoom(partyId);
        partyRepository.delete(party);

        log.info("[파티 삭제 완료] partyId={}", partyId);
    }


    public CommonPartyResponse getParties(
            Long scheduleId,
            PartyType partyType,
            TransportType transportType,
            Pageable pageable,
            Long currentUserId
    ) {
        log.debug("[파티 목록 조회] scheduleId={}, partyType={}, transportType={}, userId={}, page={}",
                scheduleId, partyType, transportType, currentUserId, pageable.getPageNumber());

        if (scheduleId != null) {
            validateScheduleExists(scheduleId);
        }

        Page<Party> partyPage = partyRepository.findPartiesWithFilters(
                scheduleId, partyType, transportType, PartyStatus.RECRUITING, pageable, currentUserId);

        return buildCommonPartyResponse(partyPage, currentUserId, null, null);
    }

    public CommonPartyResponse getMyCreatedParties(Pageable pageable, Long currentUserId) {
        log.debug("[내가 만든 파티 조회] userId={}, page={}", currentUserId, pageable.getPageNumber());

        Page<Party> partyPage = partyRepository.findActivePartiesByLeaderId(currentUserId, pageable);

        if (partyPage.isEmpty()) {
            return createEmptyResponse(pageable);
        }

        Map<Long, String> participationTypeMap = partyPage.getContent().stream()
                .collect(Collectors.toMap(Party::getId, party -> "CREATED"));

        return buildCommonPartyResponse(partyPage, currentUserId, participationTypeMap, null);
    }

    public CommonPartyResponse getMyPendingApplications(Pageable pageable, Long currentUserId) {
        log.debug("[신청중 파티 조회] userId={}, page={}", currentUserId, pageable.getPageNumber());

        Page<PartyApplication> applicationPage = partyApplicationRepository
                .findByApplicantIdAndStatusWithActiveParties(
                        currentUserId,
                        ApplicationStatus.PENDING,
                        pageable
                );

        return buildCommonPartyResponseFromApplications(applicationPage, currentUserId, "PENDING");
    }

    public CommonPartyResponse getMyJoinedParties(Pageable pageable, Long currentUserId) {
        log.debug("[참여중 파티 조회] userId={}, page={}", currentUserId, pageable.getPageNumber());

        Page<PartyMember> memberPage = partyMemberRepository
                .findActivePartiesByUserId(currentUserId, pageable);

        if (memberPage.isEmpty()) {
            return createEmptyResponse(pageable);
        }

        // 파티 ID 추출
        List<Long> partyIds = memberPage.getContent().stream()
                .map(PartyMember::getPartyId)
                .toList();

        // 파티 정보 조회
        Map<Long, Party> partyMap = getPartyMap(partyIds);

        // 활성 파티만 필터링 (RECRUITING 또는 CLOSED 상태)
        List<Party> parties = memberPage.getContent().stream()
                .map(member -> partyMap.get(member.getPartyId()))
                .filter(Objects::nonNull)
                .filter(party -> party.getStatus() == PartyStatus.RECRUITING
                        || party.getStatus() == PartyStatus.CLOSED)
                .toList();

        if (parties.isEmpty()) {
            return createEmptyResponse(pageable);
        }

        // Application 정보 조회 (applicationId, applicationStatus 표시용)
        List<Long> filteredPartyIds = parties.stream().map(Party::getId).toList();
        List<PartyApplication> applications = partyApplicationRepository
                .findByApplicantId(currentUserId).stream()
                .filter(app -> filteredPartyIds.contains(app.getPartyId()))
                .toList();

        Map<Long, PartyApplication> applicationMap = applications.stream()
                .collect(Collectors.toMap(PartyApplication::getPartyId, app -> app));

        Map<Long, String> participationTypeMap = parties.stream()
                .collect(Collectors.toMap(Party::getId, party -> "JOINED"));

        // 실제 조회된 파티 기준으로 Page 재구성
        Page<Party> partyPage = new PageImpl<>(parties, pageable, memberPage.getTotalElements());

        return buildCommonPartyResponse(partyPage, currentUserId, participationTypeMap, applicationMap);
    }

    public CommonPartyResponse getMyCompletedParties(Pageable pageable, Long currentUserId) {
        log.debug("[종료된 파티 조회] userId={}, page={}", currentUserId, pageable.getPageNumber());

        List<Long> joinedPartyIds = getJoinedCompletedPartyIds(currentUserId);

        Page<PartyRepositoryCustom.CompletedPartyWithType> completedPage =
                partyRepository.findCompletedPartiesByUserId(currentUserId, joinedPartyIds, pageable);

        if (completedPage.isEmpty()) {
            return createEmptyResponse(pageable);
        }

        List<Party> parties = completedPage.getContent().stream()
                .map(PartyRepositoryCustom.CompletedPartyWithType::party)
                .toList();

        Map<Long, String> participationTypeMap = completedPage.getContent().stream()
                .collect(Collectors.toMap(
                        data -> data.party().getId(),
                        PartyRepositoryCustom.CompletedPartyWithType::participationType
                ));


        List<Long> partyIds = parties.stream().map(Party::getId).toList();
        List<PartyApplication> applications = partyApplicationRepository
                .findByApplicantIdAndStatus(
                        currentUserId,
                        ApplicationStatus.COMPLETED,
                        PageRequest.of(0, MAX_COMPLETED_PARTIES_FETCH)
                ).getContent();

        Map<Long, PartyApplication> applicationMap = applications.stream()
                .filter(app -> partyIds.contains(app.getPartyId()))
                .collect(Collectors.toMap(PartyApplication::getPartyId, app -> app));

        Page<Party> partyPage = new PageImpl<>(parties, pageable, completedPage.getTotalElements());

        return buildCommonPartyResponse(partyPage, currentUserId, participationTypeMap, applicationMap);
    }



    @Transactional
    public ApplyToPartyResponse applyToParty(Long partyId, Long currentUserId) {
        log.info("[파티 신청] partyId={}, userId={}", partyId, currentUserId);

        Party party = getPartyOrThrow(partyId);
        User user = getUserOrThrow(currentUserId);

        validatePartyApplication(party, currentUserId);

        PartyApplication application = PartyApplication.create(
                partyId, currentUserId, party.getLeaderId());
        application = partyApplicationRepository.save(application);

        sendApplicationNotification(party, user, application.getId());

        log.info("[파티 신청 완료] partyId={}, userId={}, applicationId={}",
                partyId, currentUserId, application.getId());
        return new ApplyToPartyResponse(
                user.getNickname(), user.getAge(), user.getGender(), party.getPartyName());
    }

    @Transactional
    public void cancelApplication(Long partyId, Long applicationId, Long currentUserId) {
        log.info("[신청 취소] partyId={}, applicationId={}, userId={}",
                partyId, applicationId, currentUserId);

        Party party = getPartyOrThrow(partyId);
        PartyApplication application = getApplicationOrThrow(applicationId);
        validateApplicantPermission(application, currentUserId);
        validateApplicationNotApproved(application);

        application.cancel();

        // 2. [추가] 방장에게 갔던 기존 '신청 알림'을 찾아서 소프트 딜리트 (목록에서 사라짐)
        notificationRepository.findByApplicationIdAndNotificationType(applicationId, NotificationType.APPLY)
                .ifPresent(Notification::delete);

        notificationService.send(
                party.getLeaderId(),        // 수신자: 방장
                NotificationType.APPLY,     // 알림 타입
                "파티 신청 취소",             // 제목
                "신청자가 파티 신청을 취소했습니다.", // 내용
                party.getId(),              // 파티 ID
                application.getId()         // 신청서 ID
        );

        log.info("[신청 취소 완료] partyId={}, applicationId={}", partyId, applicationId);
    }

    @Transactional
    public AcceptApplicationResponse acceptApplication(Long partyId, Long applicationId, Long currentUserId) {
        log.info("[신청 승인] partyId={}, applicationId={}, userId={}",
                partyId, applicationId, currentUserId);

        Party party = partyRepository.findByIdWithLock(partyId)
                .orElseThrow(() -> new ServiceException(ErrorCode.PARTY_NOT_FOUND));

        validateLeaderPermission(party, currentUserId, ErrorCode.UNAUTHORIZED_PARTY_LEADER);

        PartyApplication application = getApplicationOrThrow(applicationId);
        validateApplicationNotProcessed(application);
        validatePartyNotFull(party);

        application.approve();
        partyMemberRepository.save(PartyMember.createMember(partyId, application.getApplicantId()));
        party.incrementCurrentMembers();

        sendAcceptNotification(party, application);

        log.info("[신청 승인 완료] partyId={}, applicationId={}, currentMembers={}/{}",
                partyId, applicationId, party.getCurrentMembers(), party.getMaxMembers());
        return new AcceptApplicationResponse(application.getApplicantId(), party.getPartyName(), null);
    }

    @Transactional
    public RejectApplicationResponse rejectApplication(Long partyId, Long applicationId, Long currentUserId) {
        log.info("[신청 거절] partyId={}, applicationId={}, userId={}",
                partyId, applicationId, currentUserId);

        Party party = getPartyOrThrow(partyId);
        validateLeaderPermission(party, currentUserId, ErrorCode.UNAUTHORIZED_PARTY_LEADER);

        PartyApplication application = getApplicationOrThrow(applicationId);
        validateApplicationNotProcessed(application);

        application.reject();
        sendRejectNotification(party, application);

        log.info("[신청 거절 완료] partyId={}, applicationId={}", partyId, applicationId);
        return new RejectApplicationResponse(application.getApplicantId(), party.getPartyName(), null);
    }

    public GetApplicantsResponse getApplicants(Long partyId, Long currentUserId) {
        log.debug("[신청자 목록 조회] partyId={}, userId={}", partyId, currentUserId);

        Party party = getPartyOrThrow(partyId);
        validateLeaderPermission(party, currentUserId, ErrorCode.UNAUTHORIZED_PARTY_LEADER);

        List<PartyApplication> applications = partyApplicationRepository.findByPartyId(partyId);
        Map<Long, User> userMap = getUserMap(applications.stream()
                .map(PartyApplication::getApplicantId).distinct().toList());

        List<GetApplicantsResponse.ApplicationInfo> applicationInfos = applications.stream()
                .map(application -> buildApplicationInfo(application, userMap))
                .toList();

        ApplicationCounts counts = getApplicationCounts(List.of(partyId))
                .getOrDefault(partyId, new ApplicationCounts(0, 0, 0));

        return new GetApplicantsResponse(
                partyId,
                applicationInfos,
                new GetApplicantsResponse.ApplicationSummary(
                        applications.size(),
                        counts.pending(),
                        counts.approved(),
                        counts.rejected()
                )
        );
    }



    public GetPartyMembersResponse getPartyMembers(Long partyId) {
        log.debug("[멤버 목록 조회] partyId={}", partyId);

        validatePartyExists(partyId);

        List<PartyMember> members = partyMemberRepository.findActiveMembers(partyId);
        Map<Long, User> userMap = getUserMap(members.stream()
                .map(PartyMember::getUserId).distinct().toList());

        List<GetPartyMembersResponse.MemberInfo> memberInfos = members.stream()
                .map(member -> buildMemberInfo(member, userMap))
                .toList();

        return new GetPartyMembersResponse(partyId, memberInfos, members.size());
    }

    @Transactional
    public void removePartyMember(Long partyId, Long userId) {
        log.info("[멤버 탈퇴] partyId={}, userId={}", partyId, userId);

        Party party = getPartyOrThrow(partyId);
        PartyMember member = getActiveMemberOrThrow(partyId, userId);

        member.leave(LocalDateTime.now());
        party.decrementCurrentMembers();

        log.info("[멤버 탈퇴 완료] partyId={}, userId={}, remainingMembers={}",
                partyId, userId, party.getCurrentMembers());
    }

    @Transactional
    public void kickPartyMember(Long partyId, Long targetMemberId) {
        log.info("[멤버 강퇴] partyId={}, targetMemberId={}", partyId, targetMemberId);

        Party party = getPartyOrThrow(partyId);
        PartyMember member = getActiveMemberOrThrow(partyId, targetMemberId);

        member.kick(LocalDateTime.now());
        party.decrementCurrentMembers();

        log.info("[멤버 강퇴 완료] partyId={}, targetMemberId={}, remainingMembers={}",
                partyId, targetMemberId, party.getCurrentMembers());
    }



    private CommonPartyResponse buildCommonPartyResponse(
            Page<Party> partyPage,
            Long currentUserId,
            Map<Long, String> participationTypeMap,
            Map<Long, PartyApplication> applicationMap
    ) {
        if (partyPage.isEmpty()) {
            return createEmptyResponse(partyPage.getPageable());
        }

        List<Party> parties = partyPage.getContent();
        List<Long> partyIds = parties.stream().map(Party::getId).toList();

        Map<Long, User> leaderMap = getUserMap(parties.stream()
                .map(Party::getLeaderId).distinct().toList());
        Map<Long, Schedule> scheduleMap = getScheduleMap(parties.stream()
                .map(Party::getScheduleId).distinct().toList());
        Set<Long> appliedPartyIds = partyApplicationRepository
                .findAppliedPartyIds(partyIds, currentUserId)
                .stream().collect(Collectors.toSet());

        if (applicationMap == null && !appliedPartyIds.isEmpty()) {
            List<PartyApplication> applications = partyApplicationRepository
                    .findByApplicantId(currentUserId).stream()
                    .filter(app -> partyIds.contains(app.getPartyId()))
                    .toList();

            applicationMap = applications.stream()
                    .collect(Collectors.toMap(PartyApplication::getPartyId, app -> app));
        }

        final Map<Long, PartyApplication> finalApplicationMap = applicationMap;

        List<CommonPartyResponse.PartyItem> partyItems = parties.stream()
                .map(party -> buildPartyItem(
                        party,
                        leaderMap,
                        scheduleMap,
                        currentUserId,
                        appliedPartyIds,
                        participationTypeMap,
                        finalApplicationMap
                ))
                .toList();

        return new CommonPartyResponse(
                partyItems,
                (int) partyPage.getTotalElements(),
                partyPage.getTotalPages(),
                partyPage.getNumber()
        );
    }

    private CommonPartyResponse buildCommonPartyResponseFromApplications(
            Page<PartyApplication> applicationPage,
            Long currentUserId,
            String participationType
    ) {
        if (applicationPage.isEmpty()) {
            return createEmptyResponse(applicationPage.getPageable());
        }

        List<PartyApplication> applications = applicationPage.getContent();
        List<Long> partyIds = applications.stream()
                .map(PartyApplication::getPartyId)
                .distinct()
                .toList();

        Map<Long, Party> partyMap = getPartyMap(partyIds);
        Map<Long, PartyApplication> applicationMap = applications.stream()
                .collect(Collectors.toMap(PartyApplication::getPartyId, app -> app));

        List<Party> parties = applications.stream()
                .map(app -> partyMap.get(app.getPartyId()))
                .filter(Objects::nonNull)
                .toList();

        Map<Long, User> leaderMap = getUserMap(parties.stream()
                .map(Party::getLeaderId).distinct().toList());
        Map<Long, Schedule> scheduleMap = getScheduleMap(parties.stream()
                .map(Party::getScheduleId).distinct().toList());
        Set<Long> appliedPartyIds = partyIds.stream().collect(Collectors.toSet());
        Map<Long, String> participationTypeMap = parties.stream()
                .collect(Collectors.toMap(Party::getId, party -> participationType));

        List<CommonPartyResponse.PartyItem> partyItems = parties.stream()
                .map(party -> buildPartyItem(
                        party,
                        leaderMap,
                        scheduleMap,
                        currentUserId,
                        appliedPartyIds,
                        participationTypeMap,
                        applicationMap
                ))
                .toList();

        return new CommonPartyResponse(
                partyItems,
                (int) applicationPage.getTotalElements(),
                applicationPage.getTotalPages(),
                applicationPage.getNumber()
        );
    }

    private CommonPartyResponse.PartyItem buildPartyItem(
            Party party,
            Map<Long, User> leaderMap,
            Map<Long, Schedule> scheduleMap,
            Long currentUserId,
            Set<Long> appliedPartyIds,
            Map<Long, String> participationTypeMap,
            Map<Long, PartyApplication> applicationMap
    ) {
        User leader = leaderMap.get(party.getLeaderId());
        Schedule schedule = scheduleMap.get(party.getScheduleId());

        if (leader == null || schedule == null) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        PartyApplication application = applicationMap != null ? applicationMap.get(party.getId()) : null;
        Long applicationId = application != null ? application.getId() : null;
        ApplicationStatus applicationStatus = application != null ? application.getStatus() : null;

        return new CommonPartyResponse.PartyItem(
                party.getId(),
                new CommonPartyResponse.ScheduleInfo(
                        schedule.getId(),
                        schedule.getTitle()
                ),
                new CommonPartyResponse.LeaderInfo(
                        leader.getId(),
                        leader.getNickname()
                ),
                new CommonPartyResponse.PartyDetail(
                        party.getPartyType(),
                        party.getPartyName(),
                        party.getDepartureLocation(),
                        party.getArrivalLocation(),
                        party.getTransportType(),
                        party.getMaxMembers(),
                        party.getCurrentMembers(),
                        party.getPreferredGender(),
                        party.getPreferredAge(),
                        party.getStatus(),
                        party.getDescription()
                ),
                party.getLeaderId().equals(currentUserId),
                appliedPartyIds.contains(party.getId()),
                participationTypeMap != null ? participationTypeMap.get(party.getId()) : null,
                applicationId,
                applicationStatus
        );
    }

    // 엔티티 조회 헬퍼

    private Party getPartyOrThrow(Long partyId) {
        return partyRepository.findById(partyId)
                .orElseThrow(() -> new ServiceException(ErrorCode.PARTY_NOT_FOUND));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));
    }

    private PartyApplication getApplicationOrThrow(Long applicationId) {
        return partyApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ServiceException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private PartyMember getActiveMemberOrThrow(Long partyId, Long userId) {
        return partyMemberRepository.findByPartyIdAndUserIdAndLeftAtIsNull(partyId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_IN_PARTY));
    }

    private Map<Long, User> getUserMap(List<Long> userIds) {
        return toMap(userRepository.findAllById(userIds), User::getId);
    }

    private Map<Long, Party> getPartyMap(List<Long> partyIds) {
        return toMap(partyRepository.findAllById(partyIds), Party::getId);
    }

    private Map<Long, Schedule> getScheduleMap(List<Long> scheduleIds) {
        return toMap(scheduleRepository.findAllById(scheduleIds), Schedule::getId);
    }

    private <T, K> Map<K, T> toMap(Iterable<T> iterable, Function<T, K> keyExtractor) {
        return ((List<T>) iterable).stream()
                .collect(Collectors.toMap(keyExtractor, Function.identity()));
    }

    // 검증 헬퍼

    private void validateScheduleExists(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new ServiceException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
    }

    private void validatePartyExists(Long partyId) {
        if (!partyRepository.existsById(partyId)) {
            throw new ServiceException(ErrorCode.PARTY_NOT_FOUND);
        }
    }

    private void validateLeaderPermission(Party party, Long userId, ErrorCode errorCode) {
        if (!party.isLeader(userId)) {
            log.warn("[권한 없음] partyId={}, attemptUserId={}, actualLeaderId={}",
                    party.getId(), userId, party.getLeaderId());
            throw new ServiceException(errorCode);
        }
    }

    private void validateApplicantPermission(PartyApplication application, Long userId) {
        if (!application.getApplicantId().equals(userId)) {
            log.warn("[권한 없음] applicationId={}, attemptUserId={}, actualApplicantId={}",
                    application.getId(), userId, application.getApplicantId());
            throw new ServiceException(ErrorCode.UNAUTHORIZED_PARTY_ACCESS);
        }
    }

    private void validateMaxMembersUpdate(Integer requestedMaxMembers, Integer currentMembers) {
        if (requestedMaxMembers != null && requestedMaxMembers < currentMembers) {
            log.warn("[최대 인원 축소 실패] requestedMax={}, currentMembers={}",
                    requestedMaxMembers, currentMembers);
            throw new ServiceException(ErrorCode.CANNOT_REDUCE_MAX_MEMBERS);
        }
    }

    private void validatePartyRecruiting(Party party) {
        if (!party.isRecruiting()) {
            log.warn("[모집 중이 아님] partyId={}, status={}", party.getId(), party.getStatus());
            throw new ServiceException(ErrorCode.PARTY_NOT_RECRUITING);
        }
    }

    private void validatePartyApplication(Party party, Long userId) {
        if (party.isLeader(userId)) {
            throw new ServiceException(ErrorCode.CANNOT_APPLY_OWN_PARTY);
        }
        if (party.isFull()) {
            throw new ServiceException(ErrorCode.PARTY_FULL);
        }
        if (!party.isRecruiting()) {
            throw new ServiceException(ErrorCode.PARTY_NOT_RECRUITING);
        }
        if (partyMemberRepository.existsByPartyIdAndUserId(party.getId(), userId)) {
            throw new ServiceException(ErrorCode.ALREADY_JOINED_BEFORE);
        }
        if (partyApplicationRepository.existsByPartyIdAndApplicantId(party.getId(), userId)) {
            throw new ServiceException(ErrorCode.ALREADY_APPLIED);
        }
    }

    private void validateApplicationNotApproved(PartyApplication application) {
        if (application.isApproved()) {
            log.warn("[승인된 신청 취소 시도] applicationId={}, status={}",
                    application.getId(), application.getStatus());
            throw new ServiceException(ErrorCode.CANNOT_CANCEL_APPROVED_APPLICATION);
        }
    }

    private void validateApplicationNotProcessed(PartyApplication application) {
        if (application.isProcessed()) {
            log.warn("[중복 처리 시도] applicationId={}, status={}",
                    application.getId(), application.getStatus());
            throw new ServiceException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }
    }

    private void validatePartyNotFull(Party party) {
        if (party.isFull()) {
            log.warn("[파티 정원 초과] partyId={}, current={}, max={}",
                    party.getId(), party.getCurrentMembers(), party.getMaxMembers());
            throw new ServiceException(ErrorCode.PARTY_FULL);
        }
    }

    // 알림 헬퍼

    private void sendApplicationNotification(Party party, User applicant, Long applicationId) {
        String message = String.format("%s(%d/%s)님이 '%s' 파티에 신청했습니다.",
                applicant.getNickname(), applicant.getAge(), applicant.getGender(), party.getPartyName());
        notificationService.send(
                party.getLeaderId(),
                NotificationType.APPLY,
                "새로운 파티 신청",
                message,
                party.getId(),
                applicationId
        );
    }

    private void sendAcceptNotification(Party party, PartyApplication application) {
        notificationService.send(
                application.getApplicantId(),
                NotificationType.ACCEPT,
                "파티 신청 수락",
                String.format("'%s' 파티 참여가 수락되었습니다.", party.getPartyName()),
                party.getId(),
                application.getId()
        );
    }

    private void sendRejectNotification(Party party, PartyApplication application) {
        notificationService.send(
                application.getApplicantId(),
                NotificationType.REJECT,
                "파티 신청 거절",
                String.format("'%s' 파티 참여가 거절되었습니다.", party.getPartyName()),
                party.getId(),
                application.getId()
        );
    }

    // 기타 헬퍼

    private List<Long> getJoinedCompletedPartyIds(Long userId) {
        return partyApplicationRepository
                .findByApplicantIdAndStatus(
                        userId,
                        ApplicationStatus.COMPLETED,
                        PageRequest.of(0, MAX_COMPLETED_PARTIES_FETCH)
                ).getContent().stream()
                .map(PartyApplication::getPartyId)
                .distinct()
                .toList();
    }

    private Map<Long, ApplicationCounts> getApplicationCounts(List<Long> partyIds) {
        if (partyIds.isEmpty()) {
            return Map.of();
        }

        return partyApplicationRepository.countByPartyIdsGroupByStatus(partyIds).stream()
                .collect(Collectors.groupingBy(
                        PartyApplicationRepository.ApplicationCountProjection::getPartyId,
                        Collectors.toMap(
                                PartyApplicationRepository.ApplicationCountProjection::getStatus,
                                PartyApplicationRepository.ApplicationCountProjection::getCount
                        )
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ApplicationCounts(
                                entry.getValue().getOrDefault(ApplicationStatus.PENDING, 0L).intValue(),
                                entry.getValue().getOrDefault(ApplicationStatus.APPROVED, 0L).intValue(),
                                entry.getValue().getOrDefault(ApplicationStatus.REJECTED, 0L).intValue()
                        )
                ));
    }

    private GetApplicantsResponse.ApplicationInfo buildApplicationInfo(
            PartyApplication application,
            Map<Long, User> userMap
    ) {
        User user = userMap.get(application.getApplicantId());
        if (user == null) {
            throw new ServiceException(ErrorCode.USER_NOT_FOUND);
        }
        return new GetApplicantsResponse.ApplicationInfo(
                application.getId(),
                new GetApplicantsResponse.ApplicantInfo(
                        application.getApplicantId(),
                        user.getNickname(),
                        user.getProfileImage(),
                        user.getGender(),
                        user.getAge()
                ),
                application.getStatus()
        );
    }

    private GetPartyMembersResponse.MemberInfo buildMemberInfo(
            PartyMember member,
            Map<Long, User> userMap
    ) {
        User user = userMap.get(member.getUserId());
        if (user == null) {
            throw new ServiceException(ErrorCode.USER_NOT_FOUND);
        }
        return new GetPartyMembersResponse.MemberInfo(
                member.getId(),
                member.getUserId(),
                user.getNickname(),
                user.getProfileImage(),
                member.getRole()
        );
    }

    private CommonPartyResponse createEmptyResponse(Pageable pageable) {
        return new CommonPartyResponse(List.of(), 0, 0, pageable.getPageNumber());
    }

    private record ApplicationCounts(int pending, int approved, int rejected) {}
}