package back.kalender.domain.party.entity;

import back.kalender.domain.party.enums.ApplicationStatus;
import back.kalender.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "party_applications",
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_party_applicant", columnNames = {"party_id", "applicant_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyApplication extends BaseEntity {

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "leader_id", nullable = false)
    private Long leaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApplicationStatus status;

    @Builder
    public PartyApplication(Long partyId, Long applicantId, Long leaderId) {
        this.partyId = partyId;
        this.applicantId = applicantId;
        this.leaderId = leaderId;
        this.status = ApplicationStatus.PENDING; // 기본값: 대기중
    }

    public static PartyApplication create(Long partyId, Long applicantId, Long leaderId) {
        return PartyApplication.builder()
                .partyId(partyId)
                .applicantId(applicantId)
                .leaderId(leaderId)
                .build();
    }

    public void approve() {
        this.status = ApplicationStatus.APPROVED;
    }

    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }

    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
    }

    public boolean isApproved() {
        return this.status == ApplicationStatus.APPROVED;
    }

    public boolean isProcessed() {
        return this.status != ApplicationStatus.PENDING;
    }
}