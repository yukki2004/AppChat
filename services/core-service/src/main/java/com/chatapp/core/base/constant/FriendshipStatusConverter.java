package com.chatapp.core.base.constant;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Target type is {@code Short}, not {@code Integer} — Hibernate maps Short to SMALLINT by
 *  default, matching the actual column type. */
@Converter(autoApply = true)
public class FriendshipStatusConverter implements AttributeConverter<FriendshipStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(FriendshipStatus attribute) {
        return attribute == null ? null : (short) attribute.getCode();
    }

    @Override
    public FriendshipStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : FriendshipStatus.fromCode(dbData);
    }
}
