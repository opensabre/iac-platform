package io.github.opensabre.iac.agent.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("iac_agent_version")
@EqualsAndHashCode(callSuper = true)
public class AgentVersion extends BasePo {
    private String agentId;
    private Integer versionNo;
    private String name;
    private String code;
    private String description;
    private String configJson;
    private String status;
}
