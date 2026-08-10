package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "group_member_nicknames")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMemberNicknameEntity {

    @EmbeddedId
    private GroupMemberNicknameId id;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "set_by", nullable = false)
    private UUID setBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GroupMemberNicknameEntity(UUID groupId, UUID userId, String nickname, UUID setBy) {
        this.id = new GroupMemberNicknameId(groupId, userId);
        this.nickname = nickname;
        this.setBy = setBy;
    }

    public void update(String nickname, UUID setBy) {
        this.nickname = nickname;
        this.setBy = setBy;
    }
}
