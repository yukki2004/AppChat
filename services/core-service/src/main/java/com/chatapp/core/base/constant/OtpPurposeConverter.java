package com.chatapp.core.base.constant;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Target type is {@code Short}, not {@code Integer} — Hibernate maps Short to SMALLINT by
 *  default, matching the actual column type (values only ever go up to a handful, INTEGER
 *  would just waste space). */
@Converter(autoApply = true)
public class OtpPurposeConverter implements AttributeConverter<OtpPurpose, Short> {

    @Override
    public Short convertToDatabaseColumn(OtpPurpose attribute) {
        return attribute == null ? null : (short) attribute.getCode();
    }

    @Override
    public OtpPurpose convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : OtpPurpose.fromCode(dbData);
    }
}
