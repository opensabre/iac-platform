package io.github.opensabre.iac.agent.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("iac_agent")
@EqualsAndHashCode(callSuper = true)
public class Agent extends BasePo {
    private String name;
    private String code;
    private String description;
    private String status;
    private String configJson;
    private Integer versionNo;
}
