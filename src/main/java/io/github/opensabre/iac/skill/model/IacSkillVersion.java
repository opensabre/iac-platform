package io.github.opensabre.iac.skill.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Immutable Skill configuration snapshot used for audit and rollback. */
@Data @TableName("iac_skill_version") @EqualsAndHashCode(callSuper = true)
public class IacSkillVersion extends BasePo {
    private String skillId; private Integer versionNo; private String name; private String code;
    private String description; private String instructions; private String inputSchemaJson;
    private String outputSchemaJson; private String status;
}
